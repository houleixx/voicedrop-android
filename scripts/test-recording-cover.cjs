// Run the actual Activity cover binding method against a queued executor and a tiny View host.
const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
const {execFileSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'app/src/main/java/com/baixingai/voicedrop/RecordingsActivity.java'), 'utf8');
function method(name) {
 const start=source.indexOf('    protected '+name);if(start<0)return '';
 let i=source.indexOf('{',start),depth=1,j=i+1;
 for(;depth;j++){if(source[j]==='{')depth++;if(source[j]==='}')depth--;}
 return source.slice(start,j);
}
const host = `import java.util.*;
public class CoverHost {
 static class Bitmap {}
 static class View { }
 static class FrameLayout extends View {
  static class LayoutParams { int height; LayoutParams(int w,int h,int g){height=h;} }
  List<View> children=new ArrayList<>(); LayoutParams params=new LayoutParams(44,44,0);
  int indexOfChild(View v){return children.indexOf(v);} void removeView(View v){children.remove(v);}
  void addView(View v,int i,LayoutParams p){children.add(i,v);} LayoutParams getLayoutParams(){return params;}
  void requestLayout(){}
 }
 static class ImageView extends View { enum ScaleType { CENTER_CROP } }
 static class RoundedImageView extends ImageView { Bitmap bitmap; RoundedImageView(Object c){} void setScaleType(ScaleType t){} void setImageBitmap(Bitmap b){bitmap=b;} }
 static class Gravity { static int CENTER=0; }
 static class Recording { boolean hasArticles=true; String coverPhotoKey="photos/first.jpg"; String coverJpgKey(){return "photos/cover.jpg";} }
 static class ArticleDoc { List<MinedArticle> articles=new ArrayList<>(); List<String> photos=new ArrayList<>(); }
 static class MinedArticle { String body=""; }
 static class ArticleBody { static String firstPhotoKey(String b,List<String> p){return null;} }
 static class Library { String scope="users/a/"; String ownerScope(){return scope;} ArticleDoc fetchDoc(Recording r){return null;} }
 static class PhotoService {
  static Map<String,Bitmap> cache=new HashMap<>(); static Bitmap cachedThumbnail(String k){return cache.get(k);}
  static Bitmap thumbnail(String k){return cache.get(k);}
 }
 static class Queue { List<Runnable> tasks=new ArrayList<>(); void execute(Runnable r){tasks.add(r);} void drain(){for(Runnable r:new ArrayList<>(tasks))r.run();tasks.clear();} }
 static class Main { void post(Runnable r){r.run();} }
 int recordingMetadataGeneration=0; Queue coverIo=new Queue(); Main main=new Main(); Library library=new Library();
 boolean isFinishing(){return false;} boolean isDestroyed(){return false;} int dp(int n){return n;}
 ${method('void maybeLoadRowCover(')}
 ${method('View displayRowCover(')}
 static FrameLayout bind(CoverHost h,Recording r){FrameLayout f=new FrameLayout();View placeholder=new View();f.children.add(placeholder);h.maybeLoadRowCover(r,f,placeholder);return f;}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Bitmap shown(FrameLayout f){return f.children.get(0) instanceof RoundedImageView ? ((RoundedImageView)f.children.get(0)).bitmap:null;}
 public static void main(String[] args){
  CoverHost h=new CoverHost();Recording r=new Recording();Bitmap first=new Bitmap();
  PhotoService.cache.put("users/a/photos/first.jpg",first);
  FrameLayout f=bind(h,r);require(shown(f)==first,"cached first photo must be visible BEFORE queued cover requests run");
  h.coverIo.drain();require(shown(f)==first,"missing dedicated cover must keep the visible first photo");
  Bitmap dedicated=new Bitmap();PhotoService.cache.put("users/a/photos/cover.jpg",dedicated);
  f=bind(h,r);require(shown(f)==dedicated,"cached dedicated cover must be immediate");require(h.coverIo.tasks.isEmpty(),"cached dedicated cover needs no request");
  PhotoService.cache.remove("users/a/photos/cover.jpg");r.coverPhotoKey="photos/new.jpg";
  f=bind(h,r);require(shown(f)==null,"changed photo key must not reuse old image");
  h.library.scope="users/b/";r.coverPhotoKey="photos/first.jpg";f=bind(h,r);require(shown(f)==null,"accounts must not share row covers");
  System.out.println("PASS: cached first/dedicated cover, missing dedicated, changed key, account isolation");
 }
}`;
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'vd-cover-test-'));
const bin=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin'):'';
try {fs.writeFileSync(path.join(dir,'CoverHost.java'),host);execFileSync(path.join(bin,'javac'),[path.join(dir,'CoverHost.java')],{stdio:'inherit'});execFileSync(path.join(bin,'java'),['-cp',dir,'CoverHost'],{stdio:'inherit'});}
finally {fs.rmSync(dir,{recursive:true,force:true});}

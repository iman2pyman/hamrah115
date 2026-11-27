package ir.ems115.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.text.InputType;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.SeekBar;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class PdfActivity extends Activity {
    private PdfRenderer renderer; private ParcelFileDescriptor pfd; private int pageNo=1; private ImageView image; private TextView label; private SeekBar seek; private float zoom=1f; private Bitmap bitmap;
    @Override public void onCreate(Bundle b) { super.onCreate(b); pageNo=getIntent().getIntExtra("page",1); if(pageNo>=62 && !MainActivity.premiumAccess){ new AlertDialog.Builder(this).setMessage("این بخش ویژه اشتراک فعال کافه‌بازار است.").setPositiveButton("باشه",(d,w)->finish()).setOnCancelListener(d->finish()).show(); return; } build(); try { File f=new File(getCacheDir(),"protocols.pdf"); if(!f.exists()){ try(InputStream in=getAssets().open("protocols.pdf"); FileOutputStream o=new FileOutputStream(f)){ byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0)o.write(buf,0,n); } } pfd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY); renderer=new PdfRenderer(pfd); showPage(); } catch(Exception e){ label.setText("باز کردن فایل اصلی ممکن نشد: "+e.getMessage()); } }
    private Button button(String t){Button b=new Button(this);b.setText(t);return b;}
    void build(){ LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setBackgroundColor(Color.rgb(238,243,248)); LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER); bar.setPadding(4,4,4,4); Button prev=button("❮"); prev.setOnClickListener(v->{if(pageNo>1){pageNo--;showPage();}}); Button next=button("❯"); next.setOnClickListener(v->{if(pageNo<119){if(pageNo+1>=62 && !MainActivity.premiumAccess){showLockedPageDialog();return;}pageNo++;showPage();}}); label=new TextView(this); label.setTextColor(Color.rgb(15,55,95)); label.setGravity(Gravity.CENTER); bar.addView(prev); bar.addView(label,new LinearLayout.LayoutParams(0,-2,1)); bar.addView(next); root.addView(bar); seek=new SeekBar(this);seek.setMax(118);seek.setProgress(pageNo-1);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){if(u){if(p+1>=62 && !MainActivity.premiumAccess){seek.setProgress(60);showLockedPageDialog();return;}pageNo=p+1;showPage();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});root.addView(seek);LinearLayout zoomBar=new LinearLayout(this);zoomBar.setGravity(Gravity.CENTER);Button minus=button("− زوم"), fit=button("اندازه صفحه"), plus=button("+ زوم"), jump=button("رفتن به صفحه");minus.setOnClickListener(v->{zoom=Math.max(.7f,zoom-.2f);showPage();});plus.setOnClickListener(v->{zoom=Math.min(2.5f,zoom+.2f);showPage();});fit.setOnClickListener(v->{zoom=1f;showPage();});zoomBar.addView(minus);zoomBar.addView(fit);zoomBar.addView(plus);jump.setOnClickListener(v->showJumpDialog());zoomBar.addView(jump);root.addView(zoomBar);ScrollView sc=new ScrollView(this);sc.setFillViewport(false);sc.setSmoothScrollingEnabled(true);HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setFillViewport(false);horizontal.setSmoothScrollingEnabled(true);image=new ImageView(this);image.setAdjustViewBounds(false);image.setScaleType(ImageView.ScaleType.FIT_XY);horizontal.addView(image,new HorizontalScrollView.LayoutParams(-2,-2));sc.addView(horizontal,new ScrollView.LayoutParams(-1,-2));root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root); }

    private void showLockedPageDialog(){
        new AlertDialog.Builder(this).setTitle("پروتکل ویژه")
            .setMessage("برای مشاهده این بخش، اشتراک فعال ۳۶۵ روزه کافه‌بازار لازم است.")
            .setPositiveButton("باشه",(d,w)->{if(pageNo>=62){pageNo=61;showPage();}}).show();
    }
    private void showJumpDialog(){
        EditText input=new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setHint("شماره صفحه از ۱ تا ۱۱۹");
        input.setText(String.valueOf(pageNo));
        new AlertDialog.Builder(this).setTitle("رفتن مستقیم به صفحه PDF").setView(input)
            .setNegativeButton("لغو",(d,w)->d.dismiss())
            .setPositiveButton("باز کردن",(d,w)->{
                try{int p=Integer.parseInt(input.getText().toString().trim()); if(p>=1&&p<=119){if(p>=62 && !MainActivity.premiumAccess){showLockedPageDialog();return;}pageNo=p;showPage();}else{new AlertDialog.Builder(this).setMessage("شماره صفحه باید بین ۱ تا ۱۱۹ باشد.").setPositiveButton("باشه",null).show();}}
                catch(Exception e){new AlertDialog.Builder(this).setMessage("شماره صفحه معتبر نیست.").setPositiveButton("باشه",null).show();}
            }).show();
    }
    void showPage(){try{if(renderer==null)return;PdfRenderer.Page p=renderer.openPage(pageNo-1);int baseW=getResources().getDisplayMetrics().widthPixels;int w=Math.max(1,(int)(baseW*zoom));int h=Math.max(1,(int)(w*((float)p.getHeight()/p.getWidth())));Bitmap bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bm.eraseColor(Color.WHITE);p.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);image.setImageBitmap(bm);image.setLayoutParams(new ScrollView.LayoutParams(w,h));if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();bitmap=bm;label.setText("صفحه "+pageNo+" از ۱۱۹");if(seek!=null&&seek.getProgress()!=pageNo-1)seek.setProgress(pageNo-1);p.close();}catch(Exception e){label.setText("خطا در نمایش صفحه PDF");}}
    @Override protected void onDestroy(){try{if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();if(renderer!=null)renderer.close();if(pfd!=null)pfd.close();}catch(Exception ignored){}super.onDestroy();}
}

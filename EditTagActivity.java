package com.bitoneko.music;
import android.app.Activity;import android.content.ContentValues;import android.content.Context;import android.content.Intent;import android.graphics.Bitmap;import android.graphics.BitmapFactory;import android.graphics.Color;import android.graphics.PorterDuff;import android.graphics.drawable.ColorDrawable;import android.net.Uri;import android.os.Bundle;import android.os.Environment;import android.os.ParcelFileDescriptor;import android.provider.MediaStore;import android.util.TypedValue;import android.view.View;import android.view.Window;import android.widget.*;import java.io.ByteArrayOutputStream;import java.io.File;import java.io.FileOutputStream;import java.io.OutputStream;
import org.jaudiotagger.audio.AudioFile;import org.jaudiotagger.audio.AudioFileIO;import org.jaudiotagger.tag.FieldKey;import org.jaudiotagger.tag.Tag;

public class EditTagActivity extends Activity {
    private String trackPath;
    private EditText etTitle, etArtist, etAlbum, etYear, etGenre, etTrackNum;
    private ImageView imgCover;
    private Button btnSave, btnImport, btnUndo, btnReset, btnDownload;
    private Bitmap originalBitmap = null;
    private Bitmap selectedBitmap = null;
    private boolean isResetToSystemDefault = false;
    private static final int REQ_CODE_GALLERY = 1002;
    private static final int REQ_CODE_SAF = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); requestWindowFeature(Window.FEATURE_NO_TITLE);
        trackPath = getIntent().getStringExtra("track_path"); String p = getPackageName();
        int accentColor = 0xFFFF4081;
        try { TypedValue tv = new TypedValue(); if (getTheme().resolveAttribute(getResources().getIdentifier("colorAccent", "attr", p), tv, true)) { accentColor = tv.data; } } catch(Exception e){}
        Window w = getWindow(); if (w != null) { w.setBackgroundDrawable(new ColorDrawable(0xFF434343)); if (android.os.Build.VERSION.SDK_INT >= 21) { w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS); w.setStatusBarColor(0xFF434343); } }
        float d = getResources().getDisplayMetrics().density;
        
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(0xFF434343); layout.setPadding((int)(24*d), (int)(24*d), (int)(24*d), (int)(24*d));
        
        TextView head = new TextView(this); head.setText("Nakoto Tags & Cover Editor"); head.setTextSize(22); head.setTextColor(Color.WHITE); head.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-2, -2); hLp.bottomMargin = (int)(20*d); head.setLayoutParams(hLp); layout.addView(head);

        LinearLayout artLayout = new LinearLayout(this); artLayout.setOrientation(LinearLayout.HORIZONTAL); artLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams alLp = new LinearLayout.LayoutParams(-1, -2); alLp.bottomMargin = (int)(20*d); artLayout.setLayoutParams(alLp);
        imgCover = new ImageView(this); imgCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams((int)(110*d), (int)(110*d)); icLp.rightMargin = (int)(16*d); imgCover.setLayoutParams(icLp);
        artLayout.addView(imgCover);
        
        LinearLayout btnArtContainer = new LinearLayout(this); btnArtContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bacLp = new LinearLayout.LayoutParams(0, -2, 1f); btnArtContainer.setLayoutParams(bacLp);
        
        btnImport = createMenuButton("Import from gallery", d); btnArtContainer.addView(btnImport);
        btnUndo = createMenuButton("Undo cover", d); btnArtContainer.addView(btnUndo);
        btnReset = createMenuButton("Reset to default", d); btnArtContainer.addView(btnReset);
        btnDownload = createMenuButton("Download this cover", d); btnArtContainer.addView(btnDownload);
        
        artLayout.addView(btnArtContainer); layout.addView(artLayout);

        etTitle = createField("Track Title", (int)(12*d), accentColor); layout.addView(etTitle);
        etArtist = createField("Artist", (int)(12*d), accentColor); layout.addView(etArtist);
        etAlbum = createField("Album", (int)(12*d), accentColor); layout.addView(etAlbum);
        etYear = createField("Year", (int)(12*d), accentColor); layout.addView(etYear);
        etGenre = createField("Genre", (int)(12*d), accentColor); layout.addView(etGenre);
        etTrackNum = createField("Track Number", (int)(12*d), accentColor); layout.addView(etTrackNum);
        
        btnSave = new Button(this); btnSave.setText("Save Changes"); btnSave.setTextColor(Color.WHITE); btnSave.setBackgroundColor(0xFF616161);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-1, -2); bLp.topMargin = (int)(16*d); btnSave.setLayoutParams(bLp); layout.addView(btnSave);
        
        setContentView(layout); loadMetadata();
        btnImport.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI); startActivityForResult(intent, REQ_CODE_GALLERY); } });
        btnUndo.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { undoCoverSelection(); } });
        btnReset.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { resetToSystemDefaultArt(); } });
        btnDownload.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { downloadCurrentCover(); } });
        btnSave.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { requestSafPermissions(); } });
    }

    private Button createMenuButton(String text, float d) {
        Button btn = new Button(this); btn.setText(text); btn.setTextColor(Color.WHITE); btn.setBackgroundColor(0xFF616161); btn.setTextSize(13); btn.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int)(36*d)); lp.bottomMargin = (int)(4*d); btn.setLayoutParams(lp); btn.setPadding(0,0,0,0);
        return btn;
    }

    private EditText createField(String hint, int margin, int accent) {
        EditText et = new EditText(this); et.setHint(hint); et.setHintTextColor(0xFFB0B0B0); et.setTextColor(Color.WHITE); et.setTextSize(16); et.setSingleLine(true);
        et.getBackground().setColorFilter(accent, PorterDuff.Mode.SRC_ATOP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = margin; et.setLayoutParams(lp);
        return et;
    }

    private void loadMetadata() {
        if (trackPath == null || trackPath.isEmpty()) return;
        try {
            android.media.MediaMetadataRetriever mr = new android.media.MediaMetadataRetriever(); mr.setDataSource(trackPath);
            String t = mr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
            String a = mr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String al = mr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String y = mr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR);
            String g = mr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE);
            String tn = mr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            byte[] b = mr.getEmbeddedPicture(); mr.release();
            etTitle.setText(t != null ? t : new File(trackPath).getName().replace(".mp3", ""));
            etArtist.setText(a != null ? a : "Unknown Artist");
            etAlbum.setText(al != null ? al : "Unknown Album");
            etYear.setText(y != null ? y : ""); etGenre.setText(g != null ? g : ""); etTrackNum.setText(tn != null ? tn : "");
            if (b != null) { originalBitmap = BitmapFactory.decodeByteArray(b, 0, b.length); if (originalBitmap != null) imgCover.setImageBitmap(originalBitmap); } 
            else { int dId = getResources().getIdentifier("default_art", "drawable", getPackageName()); if (dId != 0) imgCover.setImageResource(dId); }
        } catch(Exception e) {
            etTitle.setText(new File(trackPath).getName().replace(".mp3", "")); etArtist.setText("Unknown Artist"); etAlbum.setText("Unknown Album");
        }
    }

    private void undoCoverSelection() {
        selectedBitmap = null; isResetToSystemDefault = false;
        if (originalBitmap != null) { imgCover.setImageBitmap(originalBitmap); } 
        else { int dId = getResources().getIdentifier("default_art", "drawable", getPackageName()); if (dId != 0) imgCover.setImageResource(dId); }
        Toast.makeText(this, "Changes reverted to original", Toast.LENGTH_SHORT).show();
    }

    private void resetToSystemDefaultArt() {
        selectedBitmap = null; isResetToSystemDefault = true;
        int dId = getResources().getIdentifier("default_art", "drawable", getPackageName());
        if (dId != 0) imgCover.setImageResource(dId);
        Toast.makeText(this, "Reset to default artwork", Toast.LENGTH_SHORT).show();
    }

    private void downloadCurrentCover() {
        Bitmap currentArt = selectedBitmap != null ? selectedBitmap : originalBitmap;
        if (currentArt == null || isResetToSystemDefault) { Toast.makeText(this, "No artwork available to download", Toast.LENGTH_SHORT).show(); return; }
        try {
            String fName = "Cover_" + String.valueOf(System.currentTimeMillis()) + ".jpg";
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues(); v.put(MediaStore.Images.Media.DISPLAY_NAME, fName); v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nakoto_Covers");
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (u != null) { OutputStream o = getContentResolver().openOutputStream(u); currentArt.compress(Bitmap.CompressFormat.JPEG, 95, o); o.close(); }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Nakoto_Covers"); if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, fName); FileOutputStream o = new FileOutputStream(f); currentArt.compress(Bitmap.CompressFormat.JPEG, 95, o); o.close();
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
            }
            Toast.makeText(this, "Cover downloaded to Pictures/Nakoto_Covers", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "Failed to download cover", Toast.LENGTH_SHORT).show(); }
    }

    private void requestSafPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                long audioId = -1;
                android.database.Cursor cursor = getContentResolver().query(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{android.provider.MediaStore.Audio.Media._ID}, android.provider.MediaStore.Audio.Media.DATA + "=?", new String[]{trackPath}, null);
                if (cursor != null) { if (cursor.moveToFirst()) { audioId = cursor.getLong(0); } cursor.close(); }
                if (audioId != -1) {
                    Uri trackUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId);
                    java.util.List<Uri> uris = new java.util.ArrayList<>(); uris.add(trackUri);
                    android.app.PendingIntent pi = android.provider.MediaStore.createWriteRequest(getContentResolver(), uris);
                    startIntentSenderForResult(pi.getIntentSender(), REQ_CODE_SAF, null, 0, 0, 0);
                } else { executeDirectSave(); }
            } catch (Exception e) { executeDirectSave(); }
        } else { executeDirectSave(); }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_CODE_GALLERY && data != null && data.getData() != null) {
                try { Uri uri = data.getData(); java.io.InputStream is = getContentResolver().openInputStream(uri);
                    Bitmap bm = BitmapFactory.decodeStream(is); is.close();
                    if (bm != null) { selectedBitmap = bm; Intent sI = getIntent(); isResetToSystemDefault = false; imgCover.setImageBitmap(selectedBitmap); }
                } catch(Exception e) { Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show(); }
            } else if (requestCode == REQ_CODE_SAF) {
                executeDirectSave();
            }
        }
    }

    private Bitmap resizeBitmapIfNeeded(Bitmap source, int maxSize) {
        if (source == null) return null;
        int width = source.getWidth(); int height = source.getHeight();
        if (width <= maxSize && height <= maxSize) return source;
        float ratio = (float) width / (float) height;
        int newWidth = maxSize; int newHeight = maxSize;
        if (width > height) { newHeight = (int) (maxSize / ratio); } else { newWidth = (int) (maxSize * ratio); }
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }

    private void executeDirectSave() {
        try {
            if (trackPath == null || trackPath.isEmpty()) return;
            String fTitle = etTitle.getText().toString().trim(); String fArtist = etArtist.getText().toString().trim(); String fAlbum = etAlbum.getText().toString().trim();
            String fYear = etYear.getText().toString().trim(); String fGenre = etGenre.getText().toString().trim(); String fTrackNum = etTrackNum.getText().toString().trim();
            if (fTitle.isEmpty()) { Toast.makeText(this, "Title cannot be empty!", Toast.LENGTH_SHORT).show(); return; }
            if (fArtist.isEmpty()) fArtist = "Unknown Artist"; if (fAlbum.isEmpty()) fAlbum = "Unknown Album";
            final String newTitle = fTitle; final String newArtist = fArtist; final String newAlbum = fAlbum;

            File mp3File = new File(trackPath); AudioFile f = AudioFileIO.read(mp3File); Tag tag = f.getTag();
            if (tag == null) { tag = f.createDefaultTag(); f.setTag(tag); }
            tag.setField(FieldKey.TITLE, newTitle); tag.setField(FieldKey.ARTIST, newArtist); tag.setField(FieldKey.ALBUM, newAlbum);
            if (!fYear.isEmpty()) tag.setField(FieldKey.YEAR, fYear); if (!fGenre.isEmpty()) tag.setField(FieldKey.GENRE, fGenre); if (!fTrackNum.isEmpty()) tag.setField(FieldKey.TRACK, fTrackNum);
            
            if (isResetToSystemDefault) {
                tag.deleteArtworkField();
                if (MusicManager.artCache != null) { MusicManager.artCache.remove(trackPath); }
            } else if (selectedBitmap != null) {
                Bitmap optimizedBitmap = resizeBitmapIfNeeded(selectedBitmap, 600);
                ByteArrayOutputStream baos = new ByteArrayOutputStream(); optimizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] artBytes = baos.toByteArray(); baos.close();
                org.jaudiotagger.tag.images.AndroidArtwork artwork = new org.jaudiotagger.tag.images.AndroidArtwork();
                artwork.setBinaryData(artBytes); artwork.setMimeType("image/jpeg");
                tag.deleteArtworkField(); tag.setField(artwork);
                if (MusicManager.artCache != null) { MusicManager.artCache.put(trackPath, optimizedBitmap); }
            }
            AudioFileIO.write(f);

            int globalIdx = MusicManager.songPaths.indexOf(trackPath);
            if (globalIdx != -1) {
                String oldTitle = MusicManager.songTitles.get(globalIdx); MusicManager.songTitles.set(globalIdx, newTitle);
                int displayIdx = MusicManager.displayTitles.indexOf(oldTitle); if (displayIdx != -1) { MusicManager.displayTitles.set(displayIdx, newTitle); }
                MusicManager.artistCache.put(trackPath, newArtist); MusicManager.albumCache.put(trackPath, newAlbum);
            }

            ContentValues cv = new ContentValues();
            cv.put(android.provider.MediaStore.Audio.Media.TITLE, newTitle); cv.put(android.provider.MediaStore.Audio.Media.ARTIST, newArtist); cv.put(android.provider.MediaStore.Audio.Media.ALBUM, newAlbum);
            getContentResolver().update(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv, android.provider.MediaStore.Audio.Media.DATA + "=?", new String[]{trackPath});

            android.media.MediaScannerConnection.scanFile(getApplicationContext(), new String[]{trackPath}, null, new android.media.MediaScannerConnection.OnScanCompletedListener() {
                @Override public void onScanCompleted(String path, Uri uri) {
                    runOnUiThread(new Runnable() { @Override public void run() {
                        if (MusicManager.listAdapter != null) MusicManager.listAdapter.notifyDataSetChanged();
                        Toast.makeText(getApplicationContext(), "File tags & cover saved successfully!", Toast.LENGTH_SHORT).show(); finish();
                    } });
                }
            });
        } catch(Exception e) { Toast.makeText(getApplicationContext(), "Failed to modify file metadata", Toast.LENGTH_SHORT).show(); }
    }
}

package com.bitoneko.music;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.BaseAdapter;
import java.io.File;
import java.util.*;

public class MusicManager {
    public static volatile ArrayList<Long> songIds = new ArrayList<>();
    public static volatile ArrayList<String> songPaths = new ArrayList<>();
    public static volatile ArrayList<String> songTitles = new ArrayList<>();
    public static volatile ArrayList<String> displayTitles = new ArrayList<>();
    public static ArrayList<String> continueTitles = new ArrayList<>();
    public static ArrayList<String> albumsTitles = new ArrayList<>();
    public static ArrayList<String> artistsTitles = new ArrayList<>();
    public static TrackAdapter listAdapter;
    public static volatile int currentTrackIndex = -1;
    public static boolean isExpanded = false, isTracking = false, isShuffle = false, isRestoring = false;
    public static int playbackMode = 0, lastSavedPos = 0;
    public static float startY = 0;
    public static LruCache<String, Bitmap> artCache;
    public static final Map<String, String> artistCache = Collections.synchronizedMap(new HashMap<String, String>());
    public static final Map<String, String> albumCache = Collections.synchronizedMap(new HashMap<String, String>());
    public static Random rnd = new Random();
    public static final ArrayList<String> netTitles = new ArrayList<>(), netUrls = new ArrayList<>(), netArtists = new ArrayList<>(), netPaths = new ArrayList<>();
    public static final ArrayList<String> radioNames = new ArrayList<>(), radioUrls = new ArrayList<>(), radioGenres = new ArrayList<>(), radioPaths = new ArrayList<>();
    public static String currentNetOrRadioTitle = "";

    public static void loadSongs(final Activity act) {
        loadSongs(act, true);
    }

    public static void loadSongs(final Activity act, final boolean showDialog) {
        if (artCache == null) {
            artCache = new LruCache<String, Bitmap>((int)(Runtime.getRuntime().maxMemory()/1024/8)) { 
                @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount()/1024; } 
            };
        }
        
        new Thread(new Runnable() { 
            @Override public void run() {
                final ArrayList<Long> tempIds = new ArrayList<>();
                final ArrayList<String> tempPaths = new ArrayList<>();
                final ArrayList<String> tempTitles = new ArrayList<>();
                final ArrayList<String> tempAlbums = new ArrayList<>();
                final ArrayList<String> tempArtists = new ArrayList<>();
                
                android.database.Cursor c = act.getContentResolver().query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 
                    null, 
                    android.provider.MediaStore.Audio.Media.IS_MUSIC + "!=0", 
                    null, 
                    null
                );
                
                if (c != null) { 
                    int idC = c.getColumnIndex(android.provider.MediaStore.Audio.Media._ID);
                    int tC = c.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE);
                    int dC = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA);
                    int artC = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST);
                    int albC = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ALBUM);
                    
                    while (c.moveToNext()) { 
                        long id = c.getLong(idC); 
                        String t = c.getString(tC); 
                        String p = c.getString(dC); 
                        String art = c.getString(artC); 
                        String alb = c.getString(albC);
                        
                        if (p != null) {
                            tempIds.add(id); 
                            tempPaths.add(p); 
                            tempTitles.add(t != null ? t : "Unknown Track");
                            
                            String cleanArtist = (art != null && !art.trim().isEmpty()) ? art.trim() : "Unknown Artist";
                            artistCache.put(p, cleanArtist);
                            if (!tempArtists.contains(cleanArtist)) tempArtists.add(cleanArtist);
                            
                            String cleanAlbum = (alb != null && !alb.trim().isEmpty()) ? alb.trim() : "Unknown Album";
                            if (alb == null || alb.trim().isEmpty()) { 
                                File f = new File(p); 
                                cleanAlbum = f.getParentFile() != null ? f.getParentFile().getName() : "Unknown Album";
                            }
                            albumCache.put(p, cleanAlbum);
                            if (!tempAlbums.contains(cleanAlbum)) tempAlbums.add(cleanAlbum);
                        }
                    } 
                    c.close(); 
                }
                try { PlaylistActionManager.plPaths.clear(); PlaylistActionManager.scanPlDir(PlaylistActionManager.getPlDir()); } catch(Exception e){}
                
                Collections.sort(tempAlbums);
                Collections.sort(tempArtists);
                
                songIds = tempIds;
                songPaths = tempPaths;
                songTitles = tempTitles;
                albumsTitles = tempAlbums;
                artistsTitles = tempArtists;

                final ArrayList<String> tempContinue = new ArrayList<>();
                SharedPreferences sp = act.getSharedPreferences("nakoto_settings", Context.MODE_PRIVATE);
                String raw = sp.getString("continue_albums_list", "");
                if (!raw.isEmpty()) {
                    String[] items = raw.split(";");
                    for (String item : items) {
                        if (!item.trim().isEmpty() && !tempContinue.contains(item.trim())) tempContinue.add(item.trim());
                    }
                }
                continueTitles = tempContinue;
                SharedPreferences pPrefs = act.getSharedPreferences("nakoto_player_prefs", Context.MODE_PRIVATE);
                currentTrackIndex = pPrefs.getInt("queue_index", -1);

                act.runOnUiThread(new Runnable() { 
                    @Override public void run() { 
                        try {
                            MediaTabsManager.initTabs(act); 
                            switchTabCache(MediaTabsManager.currentTab);
                            
                            android.view.View mainGrid = act.findViewById(act.getResources().getIdentifier("listview1", "id", act.getPackageName()));
                            if (mainGrid != null && mainGrid instanceof android.widget.GridView) {
                                android.widget.GridView gv = (android.widget.GridView) mainGrid;
                                listAdapter = new TrackAdapter(act, displayTitles);
                                gv.setAdapter(listAdapter);
                                applyAdaptiveGrid(act, mainGrid, 160);
                            }
                            
                            MediaTabsManager.handleTabClick(act, MediaTabsManager.currentTab);
                            if (listAdapter != null) { listAdapter.notifyDataSetChanged(); }
                            
                            if (currentTrackIndex != -1) {
                                com.bitoneko.music.PlayerBottomdialogFragmentActivity.toggleMiniPlayerVisibility(true);
                            }
                            
                            com.bitoneko.music.PlayerDrawerEngine.inject(act); 
                            com.bitoneko.music.PlayerToolbarManager.updateMenuIcon(act, true); 
                            com.bitoneko.music.PlayerToolbarManager.update(act, "EMPTY", false);
                            
                            android.view.View decorView = act.getWindow().getDecorView(); 
                            android.view.View rootLayout = decorView.findViewById(android.R.id.content);
                            if (rootLayout != null) { rootLayout.setFocusable(true); rootLayout.setFocusableInTouchMode(true); rootLayout.requestFocus(); }
                        } catch(Exception ex){}
                    } 
                }); 
            } 
        }).start(); 
    }

    public static void switchTabCache(int tabIndex) {
        if (tabIndex == -1) {
            displayTitles = new ArrayList<>(continueTitles);
        } else if (tabIndex == 0) {
            displayTitles = new ArrayList<>(songTitles);
        } else if (tabIndex == 1) {
            displayTitles = new ArrayList<>(albumsTitles);
        } else if (tabIndex == 2) {
            displayTitles = new ArrayList<>(artistsTitles);
        }
    }

    public static void applyAdaptiveGrid(android.app.Activity act, android.view.View gridView, int targetItemWidthDp) {
        if (act == null || gridView == null || !(gridView instanceof android.widget.GridView)) return;
        final android.widget.GridView gv = (android.widget.GridView) gridView;
        android.util.DisplayMetrics metrics = act.getResources().getDisplayMetrics();
        float screenWidthDp = metrics.widthPixels / metrics.density;
        int calculatedColumns = (int) (screenWidthDp / targetItemWidthDp);
        if (calculatedColumns < 2) calculatedColumns = 2;
        final int finalCols = calculatedColumns;
        int targetWidthPx = (int) (targetItemWidthDp * metrics.density);
        gv.setNumColumns(finalCols);
        gv.setGravity(android.view.Gravity.CENTER);
        gv.setStretchMode(android.widget.GridView.STRETCH_COLUMN_WIDTH);
        gv.setColumnWidth(targetWidthPx);
        gv.post(new Runnable() {
            @Override public void run() { gv.setNumColumns(finalCols); gv.requestLayout(); }
        });
    }
    public static void saveCurrentState(Context ctx) { 
        try { 
            ArrayList<String> localPaths = songPaths;
            int idx = currentTrackIndex;
            if (idx != -1 && localPaths != null && idx < localPaths.size()) { 
                SharedPreferences.Editor ed = ctx.getSharedPreferences("player_prefs", Context.MODE_PRIVATE).edit(); 
                String p = localPaths.get(idx);
                ed.putString("last_path", p); 
                ed.putInt("last_position", lastSavedPos); 
                String art = artistCache.get(p);
                String alb = albumCache.get(p);
                ed.putString("last_artist_str", art != null ? art : "Unknown Artist");
                ed.putString("last_album_str", alb != null ? alb : "Unknown Album");
                ed.commit(); 
                if (alb != null && !alb.trim().isEmpty()) {
                    updateContinueListening(ctx, alb.trim());
                }
            } 
        } catch(Exception e) {} 
    }

    private static synchronized void updateContinueListening(final Context ctx, String newAlbum) {
        try {
            if (newAlbum == null || newAlbum.trim().isEmpty() || newAlbum.equalsIgnoreCase("Unknown Album")) {
                return;
            }
            if (NakotoPlaybackService.mediaPlayer == null) {
                return;
            }
            try {
                if (!NakotoPlaybackService.mediaPlayer.isPlaying()) {
                    return;
                }
            } catch(Exception ex) {
                return;
            }
            if (NakotoPlaybackService.serviceQueuePaths != null && !NakotoPlaybackService.serviceQueuePaths.isEmpty() && NakotoPlaybackService.serviceQueueIndex != -1) {
                String path = NakotoPlaybackService.serviceQueuePaths.get(NakotoPlaybackService.serviceQueueIndex);
                if (path != null && (path.startsWith("http://") || path.startsWith("https://"))) {
                    return;
                }
            }
            if (NakotoPlaybackService.serviceQueuePaths == null || NakotoPlaybackService.serviceQueuePaths.isEmpty() || NakotoPlaybackService.serviceQueueIndex == -1) {
                return;
            }

            SharedPreferences sp = ctx.getSharedPreferences("nakoto_settings", Context.MODE_PRIVATE);
            String raw = sp.getString("continue_albums_list", "");
            List<String> list = new ArrayList<>();
            if (!raw.isEmpty()) {
                String[] items = raw.split(";");
                for (String item : items) {
                    if (!item.trim().isEmpty() && !list.contains(item.trim())) list.add(item.trim());
                }
            }
            list.remove(newAlbum);
            list.add(0, newAlbum);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i));
                if (i < list.size() - 1) sb.append(";");
            }
            sp.edit().putString("continue_albums_list", sb.toString()).commit();
            continueTitles = new ArrayList<>(list);
            
            if (ctx instanceof android.app.Activity) {
                final android.app.Activity act = (android.app.Activity) ctx;
                if (!act.isFinishing()) {
                    android.os.Looper mainLooper = android.os.Looper.getMainLooper();
                    new android.os.Handler(mainLooper).post(new Runnable() {
                        @Override public void run() { 
                            try {
                                if (!act.isFinishing()) {
                                    String pkg = act.getPackageName();
                                    final android.widget.GridView gv = (android.widget.GridView) act.findViewById(act.getResources().getIdentifier("listview1", "id", pkg));
                                    
                                    if (MediaTabsManager.currentTab == -1 && gv != null) {
                                        gv.setAdapter(null);
                                    }
                                    
                                    if (listAdapter != null) {
                                        listAdapter.notifyDataSetChanged();
                                    }
                                    
                                    if (MediaTabsManager.currentTab == -1 && gv != null && listAdapter != null) {
                                        gv.setAdapter(listAdapter);
                                        gv.setSelection(0);
                                    }
                                    
                                    com.bitoneko.music.PlayerBottomdialogFragmentActivity.toggleMiniPlayerVisibility(true);
                                }
                            } catch(Exception e){}
                        }
                    });
                }
            }
        } catch(Exception e) {}
    }
}
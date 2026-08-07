// Includes management of http and https streams, which I removed from the app

package com.bitoneko.music;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.media.audiofx.EnvironmentalReverb;
import android.os.Binder;
import android.os.IBinder;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class NakotoPlaybackService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {

    private final IBinder binder = new LocalBinder();
    public static MediaPlayer mediaPlayer;
    
    public static ArrayList<String> serviceQueuePaths = new ArrayList<>();
    public static ArrayList<String> serviceQueueTitles = new ArrayList<>();
    public static ArrayList<String> serviceOriginalPaths = new ArrayList<>();
    public static ArrayList<String> serviceOriginalTitles = new ArrayList<>();
    
    public static int serviceQueueIndex = -1;
    public static int serviceRepeatMode = 0;
    public static boolean serviceIsShuffleOn = false;
    public static boolean isServicePlaying = false;
    
    public static Equalizer nanoEqualizer;
    public static EnvironmentalReverb nanoReverb;
    public static int[] equalizerBands;

    public class LocalBinder extends Binder {
        public NakotoPlaybackService getService() {
            return NakotoPlaybackService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
        }
        restoreServiceState();
    }

    public static void setServiceQueue(ArrayList<String> paths, ArrayList<String> titles, int startIndex) {
        if (paths == null || titles == null || paths.isEmpty()) return;
        serviceQueuePaths.clear();
        serviceQueueTitles.clear();
        serviceQueuePaths.addAll(paths);
        serviceQueueTitles.addAll(titles);
        serviceQueueIndex = startIndex;
        
        PlayerQueueManager.qPaths = serviceQueuePaths;
        PlayerQueueManager.qTitles = serviceQueueTitles;
        PlayerQueueManager.qIdx = serviceQueueIndex;
        
        if (serviceIsShuffleOn) {
            serviceOriginalPaths.clear();
            serviceOriginalTitles.clear();
            serviceOriginalPaths.addAll(paths);
            serviceOriginalTitles.addAll(titles);
            
            PlayerQueueManager.orgPaths = serviceOriginalPaths;
            PlayerQueueManager.orgTitles = serviceOriginalTitles;
            
            String currentPath = paths.get(startIndex);
            String currentTitle = titles.get(startIndex);
            
            ArrayList<Integer> indices = new ArrayList<>();
            for (int i = 0; i < paths.size(); i++) {
                if (i != startIndex) indices.add(i);
            }
            Collections.shuffle(indices);
            
            serviceQueuePaths.clear();
            serviceQueueTitles.clear();
            serviceQueuePaths.add(currentPath);
            serviceQueueTitles.add(currentTitle);
            
            for (int idx : indices) {
                serviceQueuePaths.add(serviceOriginalPaths.get(idx));
                serviceQueueTitles.add(serviceOriginalTitles.get(idx));
            }
            serviceQueueIndex = 0;
            PlayerQueueManager.qIdx = 0;
        }
    }
    public void toggleServiceShuffle() {
        if (serviceQueuePaths.isEmpty()) return;
        serviceIsShuffleOn = !serviceIsShuffleOn;
        String activePath = (serviceQueueIndex >= 0 && serviceQueueIndex < serviceQueuePaths.size()) ? serviceQueuePaths.get(serviceQueueIndex) : null;
        
        if (serviceIsShuffleOn) {
            serviceOriginalPaths.clear();
            serviceOriginalTitles.clear();
            serviceOriginalPaths.addAll(serviceQueuePaths);
            serviceOriginalTitles.addAll(serviceQueueTitles);
            
            PlayerQueueManager.orgPaths = serviceOriginalPaths;
            PlayerQueueManager.orgTitles = serviceOriginalTitles;
            
            int activeIdx = serviceQueueIndex;
            ArrayList<Integer> shuffleIndices = new ArrayList<>();
            for (int i = 0; i < serviceQueuePaths.size(); i++) {
                if (i != activeIdx) shuffleIndices.add(i);
            }
            Collections.shuffle(shuffleIndices);
            
            serviceQueuePaths.clear();
            serviceQueueTitles.clear();
            if (activePath != null) {
                serviceQueuePaths.add(serviceOriginalPaths.get(activeIdx));
                serviceQueueTitles.add(serviceOriginalTitles.get(activeIdx));
            }
            for (int idx : shuffleIndices) {
                serviceQueuePaths.add(serviceOriginalPaths.get(idx));
                serviceQueueTitles.add(serviceOriginalTitles.get(idx));
            }
            serviceQueueIndex = 0;
        } else {
            if (activePath != null) {
                serviceQueuePaths.clear();
                serviceQueueTitles.clear();
                serviceQueuePaths.addAll(serviceOriginalPaths);
                serviceQueueTitles.addAll(serviceOriginalTitles);
                serviceQueueIndex = serviceQueuePaths.indexOf(activePath);
            }
        }
        PlayerQueueManager.qIdx = serviceQueueIndex;
        saveServicePreferences();
    }

    public void startTrackPlayback(int index) {
        if (index < 0 || index >= serviceQueuePaths.size()) return;
        serviceQueueIndex = index;
        PlayerQueueManager.qIdx = serviceQueueIndex;
        MusicManager.currentTrackIndex = MusicManager.songPaths.indexOf(serviceQueuePaths.get(index));
        
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(serviceQueuePaths.get(index));
            mediaPlayer.prepareAsync();
        } catch (Exception e) {}
        saveServicePreferences();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mediaPlayer.start();
        initServiceAudioEffects(mediaPlayer.getAudioSessionId());
        
        try {
            MusicManager.currentTrackIndex = MusicManager.songPaths.indexOf(serviceQueuePaths.get(serviceQueueIndex));
            MusicManager.saveCurrentState(getApplicationContext());
        } catch(Exception e){}
        
        showMediaNotification();
        Intent intent = new Intent("com.bitoneko.music.TRACK_CHANGED");
        sendBroadcast(intent);
        PlayerDispatcher.showPlayerInterface(getApplicationContext());
        com.bitoneko.music.QueueBottomdialogFragmentActivity.forceSyncFromService();
        com.bitoneko.music.PlayerPanelController.forceSyncFromService();
        com.bitoneko.music.MiniPlayerController.forceSyncFromService();
        com.bitoneko.music.PlayerEffectsDialog.applyParams(null);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (serviceQueuePaths.isEmpty()) return;
        if (serviceRepeatMode == 2) {
            startTrackPlayback(serviceQueueIndex);
            return;
        }
        int nextIndex = serviceQueueIndex + 1;
        if (nextIndex >= serviceQueuePaths.size()) {
            if (serviceRepeatMode == 1) {
                startTrackPlayback(0);
            } else {
                try {
                    mediaPlayer.seekTo(0);
                    mediaPlayer.pause();
                    stopServiceVisualizer();
                    showMediaNotification();
                    
                    sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED"));
                    com.bitoneko.music.PlayerPanelController.forceSyncFromService();
                    com.bitoneko.music.MiniPlayerController.forceSyncFromService();
                    com.bitoneko.music.QueueBottomdialogFragmentActivity.forceSyncFromService();
                } catch(Exception e){}
            }
        } else {
            startTrackPlayback(nextIndex);
        }
    }

    public void playNextServiceTrack() {
        if (serviceQueuePaths.isEmpty()) return;
        
        serviceQueueIndex++;
        if (serviceQueueIndex >= serviceQueuePaths.size()) {
            if (serviceRepeatMode == 1 || serviceRepeatMode == 2) {
                serviceQueueIndex = 0;
            } else {
                serviceQueueIndex = serviceQueuePaths.size() - 1;
                return;
            }
        }
        startTrackPlayback(serviceQueueIndex);
    }

    public void playPrevServiceTrack() {
        if (serviceQueuePaths.isEmpty()) return;
        try {
            if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
                mediaPlayer.seekTo(0);
                showMediaNotification();
                sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED"));
                return;
            }
        } catch(Exception e){}

        serviceQueueIndex--;
        if (serviceQueueIndex < 0) {
            if (serviceRepeatMode == 1 || serviceRepeatMode == 2) {
                serviceQueueIndex = serviceQueuePaths.size() - 1;
            } else {
                serviceQueueIndex = 0;
            }
        }
        startTrackPlayback(serviceQueueIndex);
    }


    private android.media.session.MediaSession mediaSession;

    private void setupMediaSessionCallbacks() {
        if (mediaSession == null) return;
        mediaSession.setCallback(new android.media.session.MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    showMediaNotification();
                    sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED"));
                    com.bitoneko.music.QueueBottomdialogFragmentActivity.forceSyncFromService();
                    com.bitoneko.music.PlayerPanelController.forceSyncFromService();
                    com.bitoneko.music.MiniPlayerController.forceSyncFromService();
                }
            }
            @Override
            public void onPause() {
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                    stopServiceVisualizer();
                    showMediaNotification();
                    sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED"));
                    com.bitoneko.music.QueueBottomdialogFragmentActivity.forceSyncFromService();
                    com.bitoneko.music.PlayerPanelController.forceSyncFromService();
                    com.bitoneko.music.MiniPlayerController.forceSyncFromService();
                }
            }
            @Override
            public void onSkipToNext() {
                playNextServiceTrack();
            }
            @Override
            public void onSkipToPrevious() {
                playPrevServiceTrack();
            }
            @Override
            public void onSeekTo(long pos) {
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo((int) pos);
                    showMediaNotification();
                }
            }
        });
    }

    private void showMediaNotification() {
    try {
        String channelId = "nakoto_playback_channel";
        String trackTitle = serviceQueueTitles.get(serviceQueueIndex);
        String trackPath = serviceQueuePaths.get(serviceQueueIndex);
        
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(trackPath);
        String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        if (artist == null || artist.isEmpty()) artist = "Unknown Artist";
        
        String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long trackDuration = 0;
        if (durationStr != null) {
            trackDuration = Long.parseLong(durationStr);
        }
        
        byte[] artBytes = mmr.getEmbeddedPicture();
        Bitmap artBitmap = null;
        if (artBytes != null) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            artBitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length, opts);
        }
        mmr.release();

        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "Nakoto Playback", android.app.NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.enableLights(false);
            channel.enableVibration(false);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        if (mediaSession == null) {
            mediaSession = new android.media.session.MediaSession(this, "NakotoMediaSession");
            mediaSession.setActive(true);
            setupMediaSessionCallbacks();
        }

        android.media.MediaMetadata.Builder metaBuilder = new android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, trackTitle)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, trackDuration);
        if (artBitmap != null) {
            metaBuilder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, artBitmap);
        }
        mediaSession.setMetadata(metaBuilder.build());

        int state = android.media.session.PlaybackState.STATE_PAUSED;
        long position = 0;
        boolean isPlaying = false;
        
        try {
            if (mediaPlayer != null) {
                isPlaying = mediaPlayer.isPlaying();
                state = isPlaying ? android.media.session.PlaybackState.STATE_PLAYING : android.media.session.PlaybackState.STATE_PAUSED;
                position = mediaPlayer.getCurrentPosition();
            }
        } catch(Exception e){}
        
                float activeSpeed = 1.0f;
        try {
            activeSpeed = com.bitoneko.music.PlayerEffectsDialog.currentSpeed;
        } catch(Exception e) {
            activeSpeed = 1.0f;
        }

        android.media.session.PlaybackState.Builder stateBuilder = new android.media.session.PlaybackState.Builder()
            .setState(state, position, activeSpeed)
            .setActions(android.media.session.PlaybackState.ACTION_PLAY | android.media.session.PlaybackState.ACTION_PAUSE | 
                        android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT | android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS |
                        android.media.session.PlaybackState.ACTION_SEEK_TO);
        mediaSession.setPlaybackState(stateBuilder.build());

        android.content.Intent notificationIntent = new android.content.Intent(this, MainActivity.class);
        notificationIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, notificationIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        android.content.Intent prevIntent = new android.content.Intent(this, NakotoPlaybackService.class).setAction("ACTION_PREV");
        android.app.PendingIntent pPrev = android.app.PendingIntent.getService(this, 1, prevIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        android.content.Intent playIntent = new android.content.Intent(this, NakotoPlaybackService.class).setAction("ACTION_TOGGLE");
        android.app.PendingIntent pPlay = android.app.PendingIntent.getService(this, 2, playIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        android.content.Intent nextIntent = new android.content.Intent(this, NakotoPlaybackService.class).setAction("ACTION_NEXT");
        android.app.PendingIntent pNext = android.app.PendingIntent.getService(this, 3, nextIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(this, channelId);
        } else {
            builder = new android.app.Notification.Builder(this);
        }

        int notifIconId = getResources().getIdentifier("ic_notification_player", "drawable", getPackageName());

        builder.setContentTitle(trackTitle)
            .setContentText(artist)
            .setSmallIcon(notifIconId)
            .setContentIntent(pendingIntent)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setPriority(android.app.Notification.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .addAction(new android.app.Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", pPrev).build())
            .addAction(new android.app.Notification.Action.Builder(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, "Play/Pause", pPlay).build())
            .addAction(new android.app.Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", pNext).build());

        if (artBitmap != null) {
            builder.setLargeIcon(artBitmap);
        }

        android.app.Notification.MediaStyle mediaStyle = new android.app.Notification.MediaStyle();
        mediaStyle.setShowActionsInCompactView(0, 1, 2);
        mediaStyle.setMediaSession(mediaSession.getSessionToken());
        builder.setStyle(mediaStyle);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(101, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(101, builder.build());
        }
    } catch (Exception e) {}
}

    private static android.media.audiofx.Visualizer internalSysVis = null;

    public static void startServiceVisualizer(android.app.Activity act) {
        try {
            if (com.bitoneko.music.PlayerVisualizerView.type == 0) return;
            if (android.os.Build.VERSION.SDK_INT >= 23 && act != null) {
                if (act.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    act.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1002);
                    return;
                }
            }
            if (mediaPlayer == null || !mediaPlayer.isPlaying()) return;
            int sId = mediaPlayer.getAudioSessionId();
            if (sId <= 0) return;

            if (internalSysVis != null) {
                internalSysVis.setEnabled(false);
                internalSysVis.release();
                internalSysVis = null;
            }

            internalSysVis = new android.media.audiofx.Visualizer(sId);
            internalSysVis.setCaptureSize(128);
            internalSysVis.setDataCaptureListener(new android.media.audiofx.Visualizer.OnDataCaptureListener() {
                @Override 
                public void onWaveFormDataCapture(android.media.audiofx.Visualizer v, byte[] bytes, int rate) {
                    com.bitoneko.music.PlayerVisualizerEngine.updateData(bytes, 1);
                }
                @Override 
                public void onFftDataCapture(android.media.audiofx.Visualizer v, byte[] bytes, int rate) {
                    com.bitoneko.music.PlayerVisualizerEngine.updateData(bytes, com.bitoneko.music.PlayerVisualizerView.type);
                }
            }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, true, true);
            internalSysVis.setEnabled(true);
        } catch(Exception e){}
    }

    public static void stopServiceVisualizer() {
        try {
            if (internalSysVis != null) {
                internalSysVis.setEnabled(false);
                internalSysVis.release();
                internalSysVis = null;
            }
        } catch(Exception e){}
    }

    public void initServiceAudioEffects(int audioSessionId) {
        try {
            if (nanoEqualizer != null) nanoEqualizer.release();
            nanoEqualizer = new Equalizer(0, audioSessionId);
            nanoEqualizer.setEnabled(true);
            short bands = nanoEqualizer.getNumberOfBands();
            equalizerBands = new int[bands];
            for (short i = 0; i < bands; i++) {
                equalizerBands[i] = nanoEqualizer.getCenterFreq(i);
            }
            if (nanoReverb != null) nanoReverb.release();
            nanoReverb = new EnvironmentalReverb(0, audioSessionId);
            nanoReverb.setEnabled(true);
        } catch (Exception e) {}
    }

    private void saveServicePreferences() {
        SharedPreferences sp = getSharedPreferences("nakoto_player_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putInt("queue_index", serviceQueueIndex);
        ed.putInt("repeat_mode", serviceRepeatMode);
        ed.putBoolean("shuffle_on", serviceIsShuffleOn);
        ed.apply();
    }

    private void restoreServiceState() {
        SharedPreferences sp = getSharedPreferences("nakoto_player_prefs", Context.MODE_PRIVATE);
        serviceQueueIndex = sp.getInt("queue_index", -1);
        serviceRepeatMode = sp.getInt("repeat_mode", 0);
        serviceIsShuffleOn = sp.getBoolean("shuffle_on", false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setOnSeekCompleteListener(new android.media.MediaPlayer.OnSeekCompleteListener() {
                    @Override
                    public void onSeekComplete(android.media.MediaPlayer mp) {
                        try {
                            sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED"));
                            com.bitoneko.music.PlayerPanelController.forceSyncFromService();
                            com.bitoneko.music.MiniPlayerController.forceSyncFromService();
                        } catch(Exception e){}
                    }
                });
            } catch(Exception e){}
        }

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if ("ACTION_PLAY_INDEX".equals(action)) {
                int tIdx = intent.getIntExtra("track_index", -1);
                if (tIdx != -1) startTrackPlayback(tIdx);
            } else if ("ACTION_TOGGLE".equals(action)) {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            stopServiceVisualizer();
                        } else {
                            mediaPlayer.start();
                            android.app.Activity topAct = com.bitoneko.music.PlayerPanelController.act;
                            if (topAct == null) topAct = com.bitoneko.music.MiniPlayerController.act;
                            startServiceVisualizer(topAct);
                        }
                    } catch(Exception e){}
                    showMediaNotification();
                    sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED"));
                }
            } else if ("ACTION_NEXT".equals(action)) {
                playNextServiceTrack();
            } else if ("ACTION_PREV".equals(action)) {
                playPrevServiceTrack();
            } else if ("ACTION_TOGGLE_SHUFFLE".equals(action)) {
                toggleServiceShuffle();
                sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED"));
            } else if ("ACTION_SEEK_NOTIF".equals(action)) {
                showMediaNotification();
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override public void run() {
                        try {
                            com.bitoneko.music.PlayerPanelController.forceSyncFromService();
                            com.bitoneko.music.MiniPlayerController.forceSyncFromService();
                        } catch(Exception e){}
                    }
                }, 100);
            } else if ("ACTION_KILL_FOREGROUND".equals(action)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (mediaPlayer != null) {
                                try { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); } catch(Exception e){}
                                mediaPlayer.reset();
                            }
                            if (serviceQueuePaths != null) serviceQueuePaths.clear();
                            if (serviceQueueTitles != null) serviceQueueTitles.clear();
                            serviceQueueIndex = -1;
                        } catch(Exception e){}
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                try { stopForeground(true); } catch(Exception e){}
                                stopSelf();
                            }
                        });
                    }
                }).start();
            } else if ("ACTION_CANCEL_RADIO".equals(action)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (mediaPlayer != null) {
                                try { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); } catch(Exception e){}
                                mediaPlayer.reset();
                            }
                            if (serviceQueuePaths != null) serviceQueuePaths.clear();
                            if (serviceQueueTitles != null) serviceQueueTitles.clear();
                            if (serviceOriginalPaths != null) serviceOriginalPaths.clear();
                            if (serviceOriginalTitles != null) serviceOriginalTitles.clear();
                            serviceQueueIndex = -1;
                            
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    try { if (com.bitoneko.music.RadioActionManager.pd != null) com.bitoneko.music.RadioActionManager.pd.dismiss(); } catch(Exception e){}
                                    try {
                                        stopForeground(true);
                                        sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED"));
                                        com.bitoneko.music.MiniPlayerController.forceSyncFromService();
                                    } catch(Exception e){}
                                }
                            });
                        } catch(Exception e){}
                    }
                }).start();
            } else if ("ACTION_PLAY_RADIO_URL".equals(action)) {
                final String rUrl = intent.getStringExtra("radio_url");
                final String rTitle = intent.getStringExtra("radio_title");
                final boolean isHttpsStream = rUrl != null && rUrl.toLowerCase().startsWith("https://");
                
                if (mediaPlayer == null) {
                    try {
                        mediaPlayer = new android.media.MediaPlayer();
                    } catch(Exception e){}
                }

                if (isHttpsStream) {
                    try {
                        if (com.bitoneko.music.RadioActionManager.pd != null) com.bitoneko.music.RadioActionManager.pd.dismiss();
                        android.app.Activity topAct = com.bitoneko.music.PlayerPanelController.act;
                        if (topAct == null) topAct = com.bitoneko.music.MiniPlayerController.act;
                        
                        if (topAct != null && !topAct.isFinishing()) {
                            com.bitoneko.music.RadioActionManager.pd = new android.app.ProgressDialog(topAct);
                            com.bitoneko.music.RadioActionManager.pd.setMessage("Setting up secure stream...");
                            com.bitoneko.music.RadioActionManager.pd.setCancelable(false);
                            com.bitoneko.music.RadioActionManager.pd.show();
                        }
                    } catch(Exception e){}
                }

                if (rUrl != null && mediaPlayer != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                try { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); } catch(Exception e){}
                                mediaPlayer.reset();

                                if (serviceQueuePaths == null) serviceQueuePaths = new java.util.ArrayList<>();
                                if (serviceQueueTitles == null) serviceQueueTitles = new java.util.ArrayList<>();
                                if (serviceOriginalPaths != null) serviceOriginalPaths.clear();
                                if (serviceOriginalTitles != null) serviceOriginalTitles.clear();
                                
                                serviceQueuePaths.clear();
                                serviceQueuePaths.add(rUrl);
                                serviceQueueTitles.clear();
                                serviceQueueTitles.add(rTitle);
                                serviceQueueIndex = 0;

                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                        .build();
                                    mediaPlayer.setAudioAttributes(attrs);
                                } else {
                                    mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
                                }
                                
                                mediaPlayer.setDataSource(rUrl);
                                
                                mediaPlayer.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                                    @Override
                                    public void onPrepared(android.media.MediaPlayer mp) {
                                        try {
                                            mediaPlayer.start();
                                            NakotoPlaybackService.isServicePlaying = true;
                                            
                                            android.app.Activity topAct = com.bitoneko.music.PlayerPanelController.act;
                                            if (topAct == null) topAct = com.bitoneko.music.MiniPlayerController.act;
                                            startServiceVisualizer(topAct);

                                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try { if (com.bitoneko.music.RadioActionManager.pd != null) com.bitoneko.music.RadioActionManager.pd.dismiss(); } catch(Exception e){}
                                                    try {
                                                        showMediaNotification();
                                                        sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED"));
                                                        com.bitoneko.music.PlayerPanelController.forceSyncFromService();
                                                        com.bitoneko.music.MiniPlayerController.forceSyncFromService();
                                                        com.bitoneko.music.QueueBottomdialogFragmentActivity.forceSyncFromService();
                                                    } catch(Exception ex){}
                                                }
                                            });
                                        } catch(Exception ex){}
                                    }
                                });
                                
                                mediaPlayer.prepareAsync();
                            } catch(Exception e) {
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try { if (com.bitoneko.music.RadioActionManager.pd != null) com.bitoneko.music.RadioActionManager.pd.dismiss(); } catch(Exception e){}
                                        try { sendBroadcast(new Intent("com.bitoneko.music.TRACK_CHANGED")); } catch(Exception ex){}
                                    }
                                });
                            }
                        }
                    }).start();
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (nanoEqualizer != null) nanoEqualizer.release();
        if (nanoReverb != null) nanoReverb.release();
    }
}
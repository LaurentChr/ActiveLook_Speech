package com.speech.demo;

import static androidx.core.content.PackageManagerCompat.LOG_TAG;
import static java.lang.Math.max;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextPaint;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.activelook.activelooksdk.Glasses;
import com.activelook.activelooksdk.types.ImgSaveFormat;
//import com.activelook.activelooksdk.types.ImgStreamFormat;
import com.activelook.activelooksdk.types.Rotation;
import com.activelook.activelooksdk.types.holdFlushAction;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements RecognitionListener {
    private Intent serviceIntent, recognizerIntent;
    private static final int REQUEST_RECORD_PERMISSION = 100;
    private Spinner LangChoice, trLangChoice;
    private TextView returnedText, translatedText, largeText, GlassesBattery, fontSizeTextView, ScrollSizeTextView;
    private ToggleButton adjusmentSet;
    private ProgressBar progressBar;
    private SpeechRecognizer speech = null;
    private String langCode= Locale.getDefault().getLanguage(), trlangCode="en";
    private boolean listening = false, translating = false, glassesSetting=false;
    private final Handler clockHandler = new Handler();
    private Runnable clockRunnable;
    private final Notification notifica = new Notification();
    Translator speechTranslator;
    TranslatorOptions options_2;

    private Glasses connectedGlasses;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch sensorSwitch;
    private SeekBar luminanceSeekBar, fontSizeSeekBar, ScrollSizeSeekBar;
    private int line=0,  lineHeight=22, maxHeight=205, nbrLin=7, gbattery=0, scroll=2,
            topmrg=0, botmrg=0, lftmrg=0, rgtmrg=0;
    private String lang_Choice = "phone Default", trlang_Choice = "phone Default";
    private byte img_nb=0;
    private boolean not_firstline = false;
    private final byte[] line_image = new byte[13]; // max 12 lines
    private final int[] line_width = new int[13]; // max 12 lines
    private final String[] line_text = new String[13]; // max 12 lines

    @SuppressLint({"BatteryLife", "SetTextI18n", "DefaultLocale"})
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // get the registerd values recorded from previous usage
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        lineHeight = sharedPreferences.getInt("lineHeightTxt", 22);
        scroll = sharedPreferences.getInt("Scroll", 2);
        lang_Choice = sharedPreferences.getString("lang_Choice", "phone Default");
        langCode = getLangCode(lang_Choice);
        trlang_Choice = sharedPreferences.getString("trlang_Choice", "english");
        trlangCode = getLangCode(trlang_Choice);
        topmrg = sharedPreferences.getInt("topmrg", 0);
        botmrg = sharedPreferences.getInt("botmrg", 0);
        lftmrg = sharedPreferences.getInt("lftmrg", 0);
        rgtmrg = sharedPreferences.getInt("rgtmrg", 0);

        notifica.defaults = 0;
        String notificationChannelName="";
        NotificationChannel channel = new NotificationChannel(notificationChannelName,
                "channel", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);

        NotificationManager nMgr = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        nMgr.cancel(0);

        AudioManager amanager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        amanager.setStreamVolume(AudioManager.STREAM_ALARM, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        amanager.setStreamMute(AudioManager.STREAM_ALARM, true);
        amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
        if (!amanager.isStreamMute(AudioManager.STREAM_ALARM)) {
            amanager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0);}

        // Check location permission (needed for BLE scan)
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},0);

        if (savedInstanceState != null && ((DemoApp) this.getApplication()).isConnected()) {
            this.connectedGlasses = savedInstanceState.getParcelable("connectedGlasses");
            this.connectedGlasses.setOnDisconnected(glasses -> {glasses.disconnect();
                MainActivity.this.disconnect(); });
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = this.findViewById(R.id.toolbar);
        largeText = findViewById(R.id.largeText);
        luminanceSeekBar = this.findViewById(R.id.luminanceSeekBar);
        sensorSwitch = this.findViewById(R.id.sensorSwitch);
        GlassesBattery = this.findViewById(R.id.GlassesBattery);
        adjusmentSet = this.findViewById(R.id.adjusment_set);
        fontSizeSeekBar = this.findViewById(R.id.fontSizeSeekBar);
        fontSizeSeekBar.setProgress(lineHeight-17);
        fontSizeTextView = this.findViewById(R.id.TextSizeView);
        fontSizeTextView.setText("Text size ("+String.format("%d",(lineHeight-1))+"px) : ");
        ScrollSizeSeekBar = this.findViewById(R.id.ScrollSizeSeekBar);
        ScrollSizeSeekBar.setProgress(scroll-1);
        ScrollSizeTextView = this.findViewById(R.id.ScrollSizeView);
        ScrollSizeTextView.setText("Scroll size ("+String.format("%d",scroll)+"px) : ");

        LangChoice = this.findViewById(R.id.lang_choice);
        String[] lang_Choices = getResources().getStringArray(R.array.langChoice);
        ArrayAdapter adapter_lang = new ArrayAdapter(this, R.layout.list_item, lang_Choices);
        LangChoice.setAdapter(adapter_lang);
        LangChoice.setSelection(Arrays.asList(lang_Choices).indexOf(lang_Choice));

        trLangChoice = this.findViewById(R.id.trlang_choice);
        String[] trlang_Choices = getResources().getStringArray(R.array.langChoice);
        ArrayAdapter adapter_trlang = new ArrayAdapter(this, R.layout.list_item, trlang_Choices);
        trLangChoice.setAdapter(adapter_trlang);
        trLangChoice.setSelection(Arrays.asList(trlang_Choices).indexOf(trlang_Choice));

        options_2 = new TranslatorOptions.Builder()
                        .setSourceLanguage(Objects.requireNonNull(TranslateLanguage.fromLanguageTag(langCode)))
                        .setTargetLanguage(Objects.requireNonNull(TranslateLanguage.fromLanguageTag(trlangCode)))
                        .build();
        speechTranslator = Translation.getClient(options_2);

        DownloadConditions conditions = new DownloadConditions.Builder().build();
        speechTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @SuppressLint("RestrictedApi")
                            @Override
                            public void onSuccess(Void v) {
                                // Model downloaded successfully. Okay to start translating.
                                Log.d(LOG_TAG, "Translate : Model downloaded successfully. Okay to start translating.");
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @SuppressLint("RestrictedApi")
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Model couldn’t be downloaded or other internal error.
                                Log.d(LOG_TAG, "Translate : Model failed download : " + e.getMessage());
                            }
                        });

        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout = findViewById(R.id.toolbar_layout);
        toolBarLayout.setTitle(getTitle());
        if (connectedGlasses!=null) {
            connectedGlasses.cfgWrite("cfgTxt", 1, 1);
            connectedGlasses.imgDeleteAll();
        }
        this.updateVisibility();
        this.bindActions();
    }

    //---------------------------------------------------------------------------------

    public void start(){
        progressBar.setVisibility(View.INVISIBLE);
        speech = SpeechRecognizer.createSpeechRecognizer(this);
//        Log.i(LOG_TAG, "isRecognitionAvailable: " + SpeechRecognizer.isRecognitionAvailable(this));
        speech.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent = new Intent(RecognizerIntent.EXTRA_PREFER_OFFLINE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            recognizerIntent = new Intent(RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY);
            recognizerIntent = new Intent(RecognizerIntent.EXTRA_ENABLE_FORMATTING);
            recognizerIntent = new Intent(RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
        }
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    }

    @SuppressLint("DefaultLocale")
    private void updateVisibility() {
        final Glasses g = this.connectedGlasses;
        if (g == null) {
            this.findViewById(R.id.connected_content).setVisibility(View.GONE);
            this.findViewById(R.id.disconnected_content).setVisibility(View.VISIBLE);
        } else {
            this.findViewById(R.id.connected_content).setVisibility(View.VISIBLE);
            this.findViewById(R.id.disconnected_content).setVisibility(View.GONE);
            g.clear();
            try {g.loadConfiguration(new BufferedReader(new InputStreamReader(getAssets().open("microphone.txt"))));}
            catch (IOException e) {e.printStackTrace();}

            displayClock();

            clockRunnable = new Runnable() {
                @SuppressLint("SetTextI18n")
                @Override  // display glasses and cell phone batteries level and clock
                public void run() {displayClock();
                    if (gbattery !=0 ) {GlassesBattery.setText("Glasses battery : "+String.format("%d",gbattery)+"%");}
                    clockHandler.postDelayed(this,60000);}
            };
            clockHandler.removeCallbacks(clockRunnable);
            clockHandler.postDelayed(clockRunnable,60000); // Every minute
        }
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void bindActions() {
        // If BT is not on, request that it be enabled.
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(),
                    "Your BLUETOOTH is not open !!!\n>>>relaunch the application", Toast.LENGTH_LONG).show();
            largeText.setText("Your BlueTooth is not open !!\n\n" +
                    "Please open BlueTooth and\n\n relaunch the application.");
            largeText.setTextColor(Color.parseColor("#FF0000"));
            largeText.setTypeface(largeText.getTypeface(), Typeface.BOLD);
        }
        this.findViewById(R.id.scan).setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ScanningActivity.class);
            MainActivity.this.startActivityForResult(intent, Activity.RESULT_FIRST_USER);
        });

        this.findViewById(R.id.button_disconnect).setOnClickListener(view -> {
            MainActivity.this.sensorSwitch(true);
            connectedGlasses.sensor(true);
            MainActivity.this.disconnect();
        });

        LangChoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String old_langCode = langCode;
                RemoteModelManager modelManager = RemoteModelManager.getInstance();

                lang_Choice = (String) parent.getItemAtPosition(position);
                langCode = getLangCode(lang_Choice);
                trlangCode = getLangCode(trlang_Choice);
                savePreferences();

                if (!old_langCode.equals(langCode) && !old_langCode.equals(trlangCode)){
                    // Delete the old model if it's on the device.
                    TranslateRemoteModel old_Model =
                            new TranslateRemoteModel.Builder(Objects.requireNonNull(
                                    TranslateLanguage.fromLanguageTag(old_langCode))).build();
                    modelManager.deleteDownloadedModel(old_Model)
                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                @SuppressLint("RestrictedApi")
                                @Override
                                public void onSuccess(Void v) {
                                    // Model deleted successfully. Okay to start translating.
                                    Log.d(LOG_TAG, "Translate : Model deleted successfully. Okay to start translating.");
                                    Toast.makeText(MainActivity.this.getApplicationContext(),
                                            "Model "+TranslateLanguage.fromLanguageTag(old_langCode)+" deleted successfully",
                                            Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @SuppressLint("RestrictedApi")
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    // Model couldn’t be downloaded or other internal error.
                                    Log.d(LOG_TAG, "Translate : Model failed delete : " + e.getMessage());
                                    Toast.makeText(MainActivity.this.getApplicationContext(),
                                            "Model "+TranslateLanguage.fromLanguageTag(old_langCode)+" failed to delete",
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                }

                options_2 = new TranslatorOptions.Builder()
                        .setSourceLanguage(Objects.requireNonNull(TranslateLanguage.fromLanguageTag(langCode)))
                        .setTargetLanguage(Objects.requireNonNull(TranslateLanguage.fromLanguageTag(trlangCode)))
                        .build();
                speechTranslator = Translation.getClient(options_2);

                DownloadConditions conditions = new DownloadConditions.Builder().build();
                speechTranslator.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @SuppressLint("RestrictedApi")
                            @Override
                            public void onSuccess(Void v) {
                                // Model downloaded successfully. Okay to start translating.
                                Log.d(LOG_TAG, "Translate : Model downloaded successfully. Okay to start translating.");
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "Model "+TranslateLanguage.fromLanguageTag(langCode)+" downloaded successfully",
                                        Toast.LENGTH_LONG).show();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @SuppressLint("RestrictedApi")
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Model couldn’t be downloaded or other internal error.
                                Log.d(LOG_TAG, "Translate : Model failed download : " + e.getMessage());
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "Model "+TranslateLanguage.fromLanguageTag(langCode)+" failed to download",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            }
            public void onNothingSelected(AdapterView<?> parent) {langCode=Locale.getDefault().getLanguage();}
        });

        trLangChoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String old_trlangCode = trlangCode;
                RemoteModelManager modelManager = RemoteModelManager.getInstance();

                trlang_Choice = (String) parent.getItemAtPosition(position);
                langCode = getLangCode(lang_Choice);
                trlangCode = getLangCode(trlang_Choice);
                savePreferences();

                if (!old_trlangCode.equals(langCode) && !old_trlangCode.equals(trlangCode)){
                    // Delete the old model if it's on the device.
                    TranslateRemoteModel old_Model =
                            new TranslateRemoteModel.Builder(Objects.requireNonNull(
                                    TranslateLanguage.fromLanguageTag(old_trlangCode))).build();
                    modelManager.deleteDownloadedModel(old_Model)
                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @SuppressLint("RestrictedApi")
                                    @Override
                                    public void onSuccess(Void v) {
                                        // Model deleted successfully. Okay to start translating.
                                        Log.d(LOG_TAG, "Translate : Model deleted successfully. Okay to start translating.");
                                        Toast.makeText(MainActivity.this.getApplicationContext(),
                                                "Model "+TranslateLanguage.fromLanguageTag(old_trlangCode)+
                                                        " deleted successfully", Toast.LENGTH_LONG).show();
                                    }
                                })
                            .addOnFailureListener(new OnFailureListener() {
                                @SuppressLint("RestrictedApi")
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    // Model couldn’t be downloaded or other internal error.
                                    Log.d(LOG_TAG, "Translate : Model failed delete : " + e.getMessage());
                                    Toast.makeText(MainActivity.this.getApplicationContext(),
                                            "Model "+TranslateLanguage.fromLanguageTag(old_trlangCode)+" failed to delete",
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                }

                options_2 = new TranslatorOptions.Builder()
                        .setSourceLanguage(Objects.requireNonNull(TranslateLanguage.fromLanguageTag(langCode)))
                        .setTargetLanguage(Objects.requireNonNull(TranslateLanguage.fromLanguageTag(trlangCode)))
                        .build();
                speechTranslator = Translation.getClient(options_2);

                DownloadConditions conditions = new DownloadConditions.Builder().build();
                speechTranslator.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @SuppressLint("RestrictedApi")
                            @Override
                            public void onSuccess(Void v) {
                                // Model downloaded successfully. Okay to start translating.
                                       Log.d(LOG_TAG, "Translate : Model downloaded successfully. " +
                                               "Okay to start translating.");
                                       Toast.makeText(MainActivity.this.getApplicationContext(),
                                               "Model "+TranslateLanguage.fromLanguageTag(trlangCode)+
                                                       " downloaded successfully", Toast.LENGTH_LONG).show();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @SuppressLint("RestrictedApi")
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Model couldn’t be downloaded or other internal error.
                                Log.d(LOG_TAG, "Translate : Model failed download : " + e.getMessage());
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "Model "+TranslateLanguage.fromLanguageTag(trlangCode)+" failed to download",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            }
            public void onNothingSelected(AdapterView<?> parent) {trlangCode="en";}
        });

        sensorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> MainActivity.this.sensorSwitch(isChecked));

        luminanceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChangedValue = 0;
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
                MainActivity.this.lumaButton(progressChangedValue);}
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        fontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint("DefaultLocale")
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            // min/max progress = 0/10, min/max linHeight = 17/27, min/max nbrLin = 4/12
            { lineHeight = progress+17; nbrLin = (int) ((231-topmrg-botmrg) / lineHeight - 1);
                savePreferences();
//                fontSizeTextView.setText("Text size ("+String.format("%d",(lineHeight-1))+"px) : ");
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        ScrollSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint("DefaultLocale")
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            { scroll = progress+1; savePreferences(); }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        returnedText = findViewById(R.id.textView1);
        translatedText = findViewById(R.id.textView2);
        progressBar = findViewById(R.id.progressBar1);
        ToggleButton toggleButton1 = findViewById(R.id.toggleButton1);
        toggleButton1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {listening = true; start();
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_PERMISSION);
            }
            else {listening = false; turnOf();
                progressBar.setIndeterminate(false);
                progressBar.setVisibility(View.INVISIBLE);
                if (connectedGlasses != null) {connectedGlasses.color((byte) 0);
                    connectedGlasses.rectf((short) 117, (short) (228-topmrg), (short) 134, (short) 255);
                    connectedGlasses.color((byte) 15);}
            }
        });

        ToggleButton toggleButton2 = findViewById(R.id.toggleButton2);
        toggleButton2.setOnCheckedChangeListener((buttonView, isChecked) -> {
            translating = isChecked;
        });


        adjusmentSet.setOnCheckedChangeListener((buttonView, isChecked) -> {glassesSetting=isChecked;
            Glasses g=connectedGlasses;
            if(isChecked) {this.findViewById(R.id.adjusment_content).setVisibility(View.VISIBLE);
                if (g!=null) {g.clear(); displayClock();
                    g.rect((short) rgtmrg, (short) botmrg, (short) (303-lftmrg),(short) (255-topmrg));}
            }
            else {this.findViewById(R.id.adjusment_content).setVisibility(View.GONE);
                if (g!=null) {g.clear(); displayClock();line=0;}
            }
        });

        this.findViewById(R.id.topMinus).setOnClickListener(view -> { topmrg--; if (topmrg<0) {topmrg=0;}
            Glasses g=connectedGlasses;
            TextView topMarginTV=this.findViewById(R.id.topMargin);
            topMarginTV.setText("Top\n-"+String.format("%d",topmrg)+"px");
            savePreferences();
            if (g!=null) { g.clear(); displayClock();
            g.rect((short) rgtmrg, (short) botmrg, (short) (303-lftmrg),(short) (255-topmrg));}});
        this.findViewById(R.id.topPlus).setOnClickListener(view ->  { topmrg++; if (topmrg>50) {topmrg=50;}
            Glasses g=connectedGlasses;
            TextView topMarginTV=this.findViewById(R.id.topMargin);
            topMarginTV.setText("Top\n-"+String.format("%d",topmrg)+"px");
            savePreferences();
            if (g!=null) { g.clear(); displayClock();
                g.rect((short) rgtmrg, (short) botmrg, (short) (303-lftmrg),(short) (255-topmrg));}});
        this.findViewById(R.id.bottomMinus).setOnClickListener(view -> { botmrg--; if (botmrg<0) {botmrg=0;}
            Glasses g=connectedGlasses;
            TextView bottomMarginTV=this.findViewById(R.id.bottomMargin);
            bottomMarginTV.setText("Bottom\n-"+String.format("%d",botmrg)+"px");
            savePreferences();
            if (g!=null) { g.clear(); displayClock();
                g.rect((short) rgtmrg, (short) botmrg, (short) (303-lftmrg),(short) (255-topmrg));}});
        this.findViewById(R.id.bottomPlus).setOnClickListener(view ->  { botmrg++; if (botmrg>50) {botmrg=50;}
            Glasses g=connectedGlasses;
            TextView bottomMarginTV=this.findViewById(R.id.bottomMargin);
            bottomMarginTV.setText("Bottom\n-"+String.format("%d",botmrg)+"px");
            savePreferences();
            if (g!=null) { g.clear(); displayClock();
                g.rect((short) rgtmrg, (short) botmrg, (short) (303-lftmrg),(short) (255-topmrg));}});
        this.findViewById(R.id.leftMinus).setOnClickListener(view -> { lftmrg--; if (lftmrg<0) {lftmrg=0;}
            Glasses g=connectedGlasses;
            TextView leftMarginTV=this.findViewById(R.id.leftMargin);
            leftMarginTV.setText("Left\n-"+String.format("%d",lftmrg)+"px");
            savePreferences();
            if (g!=null) { g.clear(); displayClock();
                g.rect((short) rgtmrg, (short) botmrg, (short) (303-lftmrg),(short) (255-topmrg));}});
        this.findViewById(R.id.leftPlus).setOnClickListener(view ->  { lftmrg++; if (lftmrg>50) {lftmrg=50;}
            Glasses g=connectedGlasses;
            TextView leftMarginTV=this.findViewById(R.id.leftMargin);
            leftMarginTV.setText("Left\n-"+String.format("%d",lftmrg)+"px");
            savePreferences();
            if (g!=null) { g.clear(); displayClock();
                g.rect((short) rgtmrg, (short) botmrg, (short) (303-lftmrg),(short) (255-topmrg));}});
        this.findViewById(R.id.rightMinus).setOnClickListener(view -> { rgtmrg--; if (rgtmrg<0) {rgtmrg=0;}
            Glasses g=connectedGlasses;
            TextView rightMarginTV=this.findViewById(R.id.rightMargin);
            rightMarginTV.setText("Right\n-"+String.format("%d",rgtmrg)+"px");
            savePreferences();
            if (g!=null) { g.clear(); displayClock();
                g.rect((short) rgtmrg, (short) botmrg, (short) (303-lftmrg),(short) (255-topmrg));}});
        this.findViewById(R.id.rightPlus).setOnClickListener(view ->  { rgtmrg++; if (rgtmrg>50) {rgtmrg=50;}
            Glasses g=connectedGlasses;
            TextView rightMarginTV=this.findViewById(R.id.rightMargin);
            rightMarginTV.setText("Right\n-"+String.format("%d",rgtmrg)+"px");
            savePreferences();
            if (g!=null) { g.clear(); displayClock();
                g.rect((short) rgtmrg, (short) botmrg, (short) (303-lftmrg),(short) (255-topmrg));}});

    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {}

    @SuppressLint("RestrictedApi")
    @Override
    public void onBeginningOfSpeech() {
        Log.i(LOG_TAG, "onBeginningOfSpeech");
        progressBar.setIndeterminate(false);
        progressBar.setMax(10);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onRmsChanged(float rmsdB) {
        Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
        progressBar.setProgress((int) rmsdB);
        if(!listening){turnOf();}
    }

    @Override
    public void onBufferReceived(byte[] bytes) {}

    @Override
    public void onEndOfSpeech() {}

    @SuppressLint("RestrictedApi")
    @Override
    public void onError(int errorCode) {
//        String errorMessage = getErrorText(errorCode);
//        Log.d(LOG_TAG, "Translate : errorCode : " + errorMessage);
//        Log.d(LOG_TAG, "Translate : LANG : " + langCode + "  trLANG : " + trlangCode);
//        returnedText.setText(errorMessage);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, langCode);
        speech.startListening(recognizerIntent);
    }

    //---------------------------------------------------------------------------------

    @SuppressLint({"DefaultLocale", "SetTextI18n", "RestrictedApi"})
    @Override
    public void onResults(Bundle results) {
//        Log.i(LOG_TAG, "onResults");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2);
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        StringBuilder text = new StringBuilder();
        assert matches != null;
        for (String result : matches)
            text.append(result).append("\n");
        final String[] textfr = {text.toString()};
        returnedText.setText(textfr[0]);
        fontSizeTextView.setText("Text size ("+String.format("%d",(lineHeight-1))+"px) : ");
        ScrollSizeTextView.setText("Scroll size ("+String.format("%d",scroll)+"px) : ");

        if (translating) {
            Log.d(LOG_TAG, "Translate : LANG : " + langCode + "  trLANG : " + trlangCode);
            speechTranslator.translate(textfr[0])
                    .addOnSuccessListener(
                            new OnSuccessListener<String>() {
                                @Override
                                public void onSuccess(@NonNull String translated_Textfr) {
                                    translatedText.setText("▶  " + translated_Textfr); // Translation successful.
                                    if (translated_Textfr!=null && translated_Textfr!="")
                                      {write_glasses(translated_Textfr);}
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    translatedText.setText("No translation ...");
                                }
                            });
        } else { // *****  NOT TRANSLATING  *****
            translatedText.setText("");
            if (textfr[0]!=null && textfr[0]!="") {write_glasses(textfr[0]);}
        }

        if (gbattery !=0 ) {GlassesBattery.setText("Glasses battery : "+String.format("%d",gbattery)+"%");}

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, langCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speech.startListening(recognizerIntent);
    }

//---------------------------------------------------------------------------------

    @SuppressLint("RestrictedApi")
    public void write_glasses(String message) {
        final Glasses g = this.connectedGlasses;
        // We start to write in the glasses here
        if (g != null && !glassesSetting) {
            displayClock();
            g.cfgSet("cfgLaurent2");
            g.imgDisplay((byte) 1, (short) 117, (short) (230-topmrg)); // Microphone
            g.cfgSet("ALooK");

            // split textfr into several lines
            g.cfgSet("cfgTxt");
            g.color((byte) 0);
            g.cfgWrite("cfgTxt", 1, 1);
            g.cfgSet("cfgTxt");
            g.color((byte) 0);
            Bitmap txtimg;
            int linWidth, maxHeightmrg=maxHeight-topmrg, maxWidth=290-lftmrg-rgtmrg;
            String txtlin = "", txtlinTry = "";

            for (int j = 0; j < message.length(); j++) {
                // we first look for spaces to split the lin by words
                if (message.charAt(j) != ' '
                        && message.charAt(j) != ((char) 0xA0) && message.charAt(j) != ((char) 0x3000)
                        && message.charAt(j) != ((char) 0x2000) && message.charAt(j) != ((char) 0x2001)
                        && message.charAt(j) != ((char) 0x2002) && message.charAt(j) != ((char) 0x2003)
                        && message.charAt(j) != ((char) 0x2004) && message.charAt(j) != ((char) 0x2005)
                        && message.charAt(j) != ((char) 0x2008) && message.charAt(j) != ((char) 0x2007)
                ) // all kinds of space, non-breaking, asiatic, ... between words
                {txtlin = txtlin + message.charAt(j);}
                else {
                    txtimg = textAsBitmap(txtlin, lineHeight - 2);
                    linWidth = txtimg.getWidth();
                    Log.d(LOG_TAG, "Translate : linWidth = " + String.format("%d",linWidth));
                    if (linWidth == 0 || txtimg == null || txtlin == " ") {
                        Log.d(LOG_TAG, "Translate FAILED !!! : linWidth = " + String.format("%d", linWidth));
                    }
                    if (linWidth < maxWidth || txtlinTry.length() == 0) {txtlinTry = txtlin; txtlin = txtlin + ' ';}
                    else {// WE CAN WRITE txtlinTry in the glasses
                        Log.d(LOG_TAG, "Translate : linWidth = " + String.format("%d", linWidth));

                        // display next line to glasses
                        txtimg = textAsBitmap(txtlinTry, lineHeight - 2);
                        linWidth = txtimg.getWidth();
                        if (linWidth == 0 || txtimg == null || txtlin == " ") {
                            Log.d(LOG_TAG, "Translate FAILED !!! : linWidth = " + String.format("%d", linWidth));
                        }
                        g.cfgWrite("cfgTxt", 1, 1);
                        g.imgSave(img_nb, txtimg, ImgSaveFormat.MONO_4BPP_HEATSHRINK_SAVE_COMP);
                        // if the screen is not full
                        if (line <= nbrLin) {
                            line_image[line] = img_nb; line_width[line] = linWidth;
                                line_text[line] = "0_"+txtlinTry;
                            g.cfgWrite("cfgTxt", 1, 1);
                            g.imgSave(img_nb, txtimg, ImgSaveFormat.MONO_4BPP_HEATSHRINK_SAVE_COMP);
                            g.cfgSet("cfgTxt");
                            g.imgDisplay(img_nb, (short) (303-lftmrg - linWidth), (short) (maxHeightmrg - line * lineHeight));
                        }
                        // if the screen is full, i.e. we have reached the max number of lines :
                        if (line > nbrLin) {
                            if (not_firstline) {
                                // every lines are shifted up in the list
                                for (int k = 0; k <= nbrLin; k++) {
                                    line_image[k] = line_image[k + 1];
                                    line_width[k] = line_width[k + 1];
                                    line_text[k] = line_text[k + 1];
                            Log.d(LOG_TAG, "Translate : ligne : " + k
                                    + " - line_image : " + line_image[k]
                                    + " - line_width : " + line_width[k]
                                    + " - line_text : " + line_text[k]);} }
                            not_firstline = true;
                            line_image[line] = img_nb; line_width[line] = linWidth; line_text[line] = txtlinTry;
                            Log.d(LOG_TAG, "Translate : ligne : " + line
                                    + " - line_image : " + line_image[line]
                                    + " - line_width : " + line_width[line]
                                    + " - line_text : " + line_text[line]);                            g.cfgWrite("cfgTxt", 1, 1);
                            g.imgSave(img_nb, txtimg, ImgSaveFormat.MONO_4BPP_HEATSHRINK_SAVE_COMP);

                            // lines are shifted up on the screen
                            g.color((byte) 0);
                            for (int l=lineHeight; l>=0; l=l-scroll) {if (l<scroll) {l=0;}
                                g.cfgSet("cfgTxt");
                                g.holdFlush(holdFlushAction.HOLD);
                                // delete screen
                                g.rectf((short) 0, (short) (256-28-topmrg), (short) 304, (short) 0);
                                g.rectf((short) 0, (short) (maxHeightmrg - lineHeight), (short) 304, (short) 0);
                                // each line is displayed
                                for (int k = 1; k <= nbrLin; k++) {
                                    g.imgDisplay(line_image[k], (short) (303 - lftmrg - line_width[k]),
                                            (short) (maxHeightmrg - l - (k-1) * lineHeight));}
                                g.holdFlush(holdFlushAction.FLUSH);
                            }
                            g.imgSave(img_nb, txtimg, ImgSaveFormat.MONO_4BPP_HEATSHRINK_SAVE_COMP);
                            // the last lines is written here
                            g.imgDisplay(img_nb, (short) (303-lftmrg - linWidth),
                                    (short) (maxHeightmrg - nbrLin * lineHeight));
                        }

                        img_nb++; if (img_nb>12) {img_nb=0;}
                        line++; if (line > nbrLin) {line = nbrLin + 1;} // the line counter is clamped at nbrLin+1
                        txtlin = txtlin.substring(txtlinTry.length() + 1) + ' ';
                        txtlinTry = "";
                    }
                }
            }

            txtimg = textAsBitmap(txtlin, lineHeight - 2);
            linWidth = txtimg.getWidth();
            if (linWidth == 0 || txtimg == null || txtlin == " ") {
                Log.d(LOG_TAG, "Translate FAILED !!! : linWidth = " + String.format("%d", linWidth));
            }
            g.cfgWrite("cfgTxt", 1, 1);
            g.imgSave(img_nb, txtimg, ImgSaveFormat.MONO_4BPP_HEATSHRINK_SAVE_COMP);
            if (line <= nbrLin) {
                line_image[line] = img_nb; line_width[line] = linWidth; line_text[line] = "1_"+txtlin;
                g.cfgWrite("cfgTxt", 1, 1);
                g.imgSave(img_nb, txtimg, ImgSaveFormat.MONO_4BPP_HEATSHRINK_SAVE_COMP);
                g.cfgSet("cfgTxt");
                g.imgDisplay(img_nb, (short) (303-lftmrg - linWidth),
                        (short) (maxHeightmrg - line * lineHeight));
            }
            // if the screen is full, i.e. we have reached the max number of lines :
            if (line > nbrLin) {
                // every lines are shifted up in the list
                if (not_firstline) { for (int k = 0; k <= nbrLin; k++) {
                    line_image[k] = line_image[k + 1];
                    line_width[k] = line_width[k + 1];
                    line_text[k] = line_text[k + 1];
                    Log.d(LOG_TAG, "Translate : ligne : " + k
                            + " - line_image : " + line_image[k]
                            + " - line_width : " + line_width[k]
                            + " - line_text : " + line_text[k]);} }
                not_firstline = true;
                line_image[line] = img_nb; line_width[line] = linWidth; line_text[line] = "2_"+txtlin;
                Log.d(LOG_TAG, "Translate : ligne : " + line
                        + " - line_image : " + line_image[line]
                        + " - line_width : " + line_width[line]
                        + " - line_text : " + line_text[line]);
                g.cfgWrite("cfgTxt", 1, 1);
                g.imgSave(img_nb, txtimg, ImgSaveFormat.MONO_4BPP_HEATSHRINK_SAVE_COMP);

                // lines are shifted up on the screen by pixel line 1 per 1
                g.color((byte) 0);
                for (int l=lineHeight; l>=0; l=l-scroll) {if (l<scroll) {l=0;}
                    g.holdFlush(holdFlushAction.HOLD);
                    // delete screen
                    g.rectf((short) 0, (short) (maxHeightmrg - lineHeight), (short) 304, (short) 0);
                    g.rectf((short) 0, (short) (256-28-topmrg), (short) 304, (short) 0);
                    // each line is displayed
                    g.cfgSet("cfgTxt");
                    for (int k = 1; k <= nbrLin; k++) {
                        g.imgDisplay(line_image[k], (short) (303 - lftmrg - line_width[k]),
                                (short) (maxHeightmrg - (k-1)*lineHeight - l));}
                    g.holdFlush(holdFlushAction.FLUSH);
                }
                g.imgSave(img_nb, txtimg, ImgSaveFormat.MONO_4BPP_HEATSHRINK_SAVE_COMP);
                // the last lines is written here
                g.cfgSet("cfgTxt");
                g.imgDisplay(img_nb, (short) (303-lftmrg - linWidth), (short) (maxHeightmrg - nbrLin * lineHeight));
            }
            line++; if (line > nbrLin) {line = nbrLin + 1;}
            img_nb++; if (img_nb>12) {img_nb=0;}
        }
    }

//---------------------------------------------------------------------------------

    public Bitmap textAsBitmap(String text, int textSize) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(textSize);
        tp.setColor(Color.WHITE); // white for text
        tp.setTextAlign(Paint.Align.LEFT);
        float baseline = -tp.ascent(); // ascent() is negative
        int width = max((int) (tp.measureText(text) + 0.5f),1); // round with 1 as min
        int height = max((int) (baseline + tp.descent() + 0.5f),1);
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setStyle(Paint.Style.FILL);
        bp.setColor(Color.BLACK); // black for background
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(image);
        c.drawPaint(bp);
        c.drawText(text, 0, baseline, tp);
        return image;
    }

    @Override
    public void onPartialResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        StringBuilder text = new StringBuilder();
        for (String result : matches)
            text.append(result).append("\n");
//        Log.i(LOG_TAG, "onPartialResults="+text);
    }

    @Override
    public void onEvent(int i, Bundle bundle) {}

    public void turnOf(){speech.stopListening(); speech.destroy();
        speechTranslator.close();}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "start talk...", Toast.LENGTH_SHORT).show();
                speech.startListening(recognizerIntent);}
            else {Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();}
        }
    }

    public String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: message = "Audio recording error"; break;
            case SpeechRecognizer.ERROR_CLIENT: message = "Client side error"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Insufficient permissions"; break;
            case SpeechRecognizer.ERROR_NETWORK: message = "Network error"; break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Network timeout"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: message = "No match"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "RecognitionService busy";
                turnOf(); break;
            case SpeechRecognizer.ERROR_SERVER: message = "error from server"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No speech input"; break;
            default: message = "Didn't understand, please try again."; break;
        }
        return message;
    }

    /////////  LUMINANCE  bar and switch
    private void lumaButton(int luma) {this.connectedGlasses.luma((byte) luma);}
    private void sensorSwitch(boolean on) {this.connectedGlasses.sensor(on);}

    @SuppressLint("DefaultLocale")
    private void displayClock(){
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        String clock = sdf.format(new Date());
        int top=255-topmrg;
        final Glasses g = connectedGlasses;
        if (g != null) {
            g.battery(r1 -> { gbattery=r1;
                connectedGlasses.cfgSet("ALooK");
                if (r1 < 25) {connectedGlasses.imgDisplay((byte) 1, (short) (272-lftmrg), (short) (top-26));}
                else {connectedGlasses.imgDisplay((byte) 0, (short) (272-lftmrg), (short) (top-26));}
                connectedGlasses.txt(new Point((263-lftmrg), top), Rotation.TOP_LR, (byte) 1, (byte) 0x0F,
                        String.format("%d", r1) + "% / " + String.format("%d", batLevel) + "%  ");
                connectedGlasses.txt(new Point(100+rgtmrg, top), Rotation.TOP_LR, (byte) 1, (byte) 0x0F, clock);
            });//Glasses Battery
        }
    }

    @SuppressLint("SetTextI18n")
    private void setUIGlassesInformations() {
        final Glasses glasses = this.connectedGlasses;
        glasses.settings(r -> sensorSwitch.setChecked(r.isGestureEnable()));
        glasses.settings(r -> luminanceSeekBar.setProgress(r.getLuma()));
    }

    @SuppressLint({"SetTextI18n", "SuspiciousIndentation"})
    private void savePreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("topmrg",topmrg);
        editor.putInt("botmrg",botmrg);
        editor.putInt("lftmrg",lftmrg);
        editor.putInt("rgtmrg",rgtmrg);
        editor.putInt("lineHeightTxt",lineHeight);
        editor.putInt("Scroll",scroll);
        editor.putString("lang_Choice",String.valueOf(LangChoice.getSelectedItem()));
        editor.putString("trlang_Choice",String.valueOf(trLangChoice.getSelectedItem()));
        editor.apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == requestCode && requestCode == Activity.RESULT_FIRST_USER) {
            if (data != null && data.hasExtra("connectedGlasses")) {
                this.connectedGlasses = data.getExtras().getParcelable("connectedGlasses");
                this.connectedGlasses.setOnDisconnected(glasses -> MainActivity.this.disconnect());
                runOnUiThread(MainActivity.this::setUIGlassesInformations);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        if (this.connectedGlasses != null) {savedInstanceState.putParcelable("connectedGlasses",
                this.connectedGlasses);}
        super.onSaveInstanceState(savedInstanceState);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();
        // If BT is not on, request that it be enabled.
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Your BlueTooth is not open !!!",
                    Toast.LENGTH_LONG).show();
            largeText.setText("Your BlueTooth is not open !!\n\n" +
                    "Please open BlueTooth and\n\n relaunch the application.");
            largeText.setTextColor(Color.parseColor("#FF0000"));
            largeText.setTypeface(largeText.getTypeface(), Typeface.BOLD);
        }
        if (!((DemoApp) this.getApplication()).isConnected()) {this.connectedGlasses = null;}
        this.updateVisibility();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Your BlueTooth is not open !!!",
                    Toast.LENGTH_LONG).show();
            largeText.setText("Your BlueTooth is not open !!\n\n" +
                    "Please open BlueTooth and\n\n relaunch the application.");
            largeText.setTextColor(Color.parseColor("#FF0000"));
            largeText.setTypeface(largeText.getTypeface(), Typeface.BOLD);
        }
        if (!((DemoApp) this.getApplication()).isConnected()) {this.connectedGlasses = null;}
    }

    protected void onPause() {super.onPause(); }

    protected void onStop() {super.onStop();
//        connectedGlasses.cfgWrite("cfgTxt", 1, 1);
        if(connectedGlasses!=null) {connectedGlasses.cfgDelete("cfgTxt");}
        if(clockHandler != null)
            clockHandler.removeCallbacks(clockRunnable); // On arrete le callback
    }

    protected void onDestroy() {super.onDestroy(); speechTranslator.close();
        if (serviceIntent != null) { stopService(serviceIntent); serviceIntent = null; }
        if(clockHandler != null)
            clockHandler.removeCallbacks(clockRunnable); // On arrete le callback
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        final Glasses g = this.connectedGlasses;

        //noinspection SimplifiableIfStatement
        if (id == R.id.about_app) {Toast.makeText(this.getApplicationContext(),
                getString(R.string.app_name) + "\nVersion " + getString(R.string.app_version),
                Toast.LENGTH_LONG).show();
            return true;}
        if (id == R.id.about_glasses) {
            if( g!=null) {Toast.makeText(this.getApplicationContext(),
                    "Glasses Name : " + g.getName() + "\n"
                            + "Firmware : " + g.getDeviceInformation().getFirmwareVersion(),
                    Toast.LENGTH_LONG).show();}
            else {Toast.makeText(this.getApplicationContext(),
                    "No connected glasses found yet!",
                    Toast.LENGTH_LONG).show();}
            return true;}
        return super.onOptionsItemSelected(item);
    }

    private void disconnect() {
        runOnUiThread(() -> {
            ((DemoApp) this.getApplication()).onDisconnected();
            MainActivity.this.connectedGlasses.disconnect();
            MainActivity.this.connectedGlasses = null;
            MainActivity.this.updateVisibility();
        });
    }

    private String getLangCode(String Choice) {
        String newLangCode = "";
        switch (Choice) {
            case "phone Default" : newLangCode = Locale.getDefault().getLanguage(); break;
            case "Afaraf" : newLangCode = "aa"; break;
            case "Аҧсуа" : newLangCode = "ab"; break;
            case "Avesta" : newLangCode = "ae"; break;
            case "Afrikaans" : newLangCode = "af"; break;
            case "Akan" : newLangCode = "ak"; break;
            case "አማርኛ" : newLangCode = "am"; break;
            case "Aragonés" : newLangCode = "an"; break;
            case "العربية" : newLangCode = "ar"; break;
            case "অসমীয়া" : newLangCode = "as"; break;
            case "авар мацӀ" : newLangCode = "av"; break;
            case "Aymar aru" : newLangCode = "ay"; break;
            case "Azərbaycan dili" : newLangCode = "az"; break;
            case "башҡорт теле" : newLangCode = "ba"; break;
            case "Беларуская" : newLangCode = "be"; break;
            case "български език" : newLangCode = "bg"; break;
            case "भोजपुरी" : newLangCode = "bh"; break;
            case "Bislama" : newLangCode = "bi"; break;
            case "Bamanankan" : newLangCode = "bm"; break;
            case "বাংলা" : newLangCode = "bn"; break;
            case "བོད་ཡིག" : newLangCode = "bo"; break;
            case "Brezhoneg" : newLangCode = "br"; break;
            case "Bosanski jezik" : newLangCode = "bs"; break;
            case "Català" : newLangCode = "ca"; break;
            case "нохчийн мотт" : newLangCode = "ce"; break;
            case "Chamoru" : newLangCode = "ch"; break;
            case "Corsu" : newLangCode = "co"; break;
            case "ᓀᐦᐃᔭᐍᐏᐣ" : newLangCode = "cr"; break;
            case "Česky" : newLangCode = "cs"; break;
            case "Словѣньскъ" : newLangCode = "cu"; break;
            case "чӑваш чӗлхи" : newLangCode = "cv"; break;
            case "Cymraeg" : newLangCode = "cy"; break;
            case "Dansk" : newLangCode = "da"; break;
            case "Deutsch" : newLangCode = "de"; break;
            case "ދިވެހި" : newLangCode = "dv"; break;
            case "རྫོང་ཁ" : newLangCode = "dz"; break;
            case "Ɛʋɛgbɛ" : newLangCode = "ee"; break;
            case "Ελληνικά" : newLangCode = "el"; break;
            case "English" : newLangCode = "en"; break;
            case "Esperanto" : newLangCode = "eo"; break;
            case "Español" : newLangCode = "es"; break;
            case "Eesti keel" : newLangCode = "et"; break;
            case "Euskara" : newLangCode = "eu"; break;
            case "فارسی" : newLangCode = "fa"; break;
            case "Fulfulde" : newLangCode = "ff"; break;
            case "Suomen kieli" : newLangCode = "fi"; break;
            case "Vosa Vakaviti" : newLangCode = "fj"; break;
            case "Føroyskt" : newLangCode = "fo"; break;
            case "Français" : newLangCode = "fr"; break;
            case "Frysk" : newLangCode = "fy"; break;
            case "Gaeilge" : newLangCode = "ga"; break;
            case "Gàidhlig" : newLangCode = "gd"; break;
            case "Galego" : newLangCode = "gl"; break;
            case "Avañe'ẽ" : newLangCode = "gn"; break;
            case "ગુજરાતી" : newLangCode = "gu"; break;
            case "Ghaelg" : newLangCode = "gv"; break;
            case "هَوُسَ" : newLangCode = "ha"; break;
            case "עברית" : newLangCode = "he"; break;
            case "हिन्दी" : newLangCode = "hi"; break;
            case "Hiri Motu" : newLangCode = "ho"; break;
            case "Hrvatski" : newLangCode = "hr"; break;
            case "Kreyòl ayisyen" : newLangCode = "ht"; break;
            case "magyar" : newLangCode = "hu"; break;
            case "Հայերեն" : newLangCode = "hy"; break;
            case "Otjiherero" : newLangCode = "hz"; break;
            case "Interlingua" : newLangCode = "ia"; break;
            case "Bahasa Indonesia" : newLangCode = "id"; break;
            case "Interlingue" : newLangCode = "ie"; break;
            case "Igbo" : newLangCode = "ig"; break;
            case "ꆇꉙ" : newLangCode = "ii"; break;
            case "Iñupiaq" : newLangCode = "ik"; break;
            case "Ido" : newLangCode = "io"; break;
            case "Íslenska" : newLangCode = "is"; break;
            case "Italiano" : newLangCode = "it"; break;
            case "ᐃᓄᒃᑎᑐᑦ" : newLangCode = "iu"; break;
            case "日本語" : newLangCode = "ja"; break;
            case "Basa Jawa" : newLangCode = "jv"; break;
            case "ქართული" : newLangCode = "ka"; break;
            case "KiKongo" : newLangCode = "kg"; break;
            case "Gĩkũyũ" : newLangCode = "ki"; break;
            case "Kuanyama" : newLangCode = "kj"; break;
            case "Қазақ тілі" : newLangCode = "kk"; break;
            case "Kalaallisut" : newLangCode = "kl"; break;
            case "ភាសាខ្មែរ" : newLangCode = "km"; break;
            case "ಕನ್ನಡ" : newLangCode = "kn"; break;
            case "한국어" : newLangCode = "ko"; break;
            case "Kanuri" : newLangCode = "kr"; break;
            case "कश्मीरी" : newLangCode = "ks"; break;
            case "Kurdî" : newLangCode = "ku"; break;
            case "коми кыв" : newLangCode = "kv"; break;
            case "Kernewek" : newLangCode = "kw"; break;
            case "кыргыз тили" : newLangCode = "ky"; break;
            case "Latine" : newLangCode = "la"; break;
            case "Lëtzebuergesch" : newLangCode = "lb"; break;
            case "Luganda" : newLangCode = "lg"; break;
            case "Limburgs" : newLangCode = "li"; break;
            case "Lingála" : newLangCode = "ln"; break;
            case "ພາສາລາວ" : newLangCode = "lo"; break;
            case "Lietuvių kalba" : newLangCode = "lt"; break;
            case "tshiluba" : newLangCode = "lu"; break;
            case "Latviešu valoda" : newLangCode = "lv"; break;
            case "Fiteny malagasy" : newLangCode = "mg"; break;
            case "Kajin M̧ajeļ" : newLangCode = "mh"; break;
            case "Te reo Māori" : newLangCode = "mi"; break;
            case "македонски јазик" : newLangCode = "mk"; break;
            case "മലയാളം" : newLangCode = "ml"; break;
            case "Монгол" : newLangCode = "mn"; break;
            case "лимба молдовеняскэ" : newLangCode = "mo"; break;
            case "मराठी" : newLangCode = "mr"; break;
            case "Bahasa Melayu" : newLangCode = "ms"; break;
            case "Malti" : newLangCode = "mt"; break;
            case "ဗမာစာ" : newLangCode = "my"; break;
            case "Ekakairũ Naoero" : newLangCode = "na"; break;
            case "Norsk bokmål" : newLangCode = "nb"; break;
            case "isiNdebele" : newLangCode = "nd"; break;
            case "नेपाली" : newLangCode = "ne"; break;
            case "Owambo" : newLangCode = "ng"; break;
            case "Nederlands" : newLangCode = "nl"; break;
            case "Norsk nynorsk" : newLangCode = "nn"; break;
            case "Norsk" : newLangCode = "no"; break;
            case "Ndébélé" : newLangCode = "nr"; break;
            case "Diné bizaad" : newLangCode = "nv"; break;
            case "ChiCheŵa" : newLangCode = "ny"; break;
            case "Occitan" : newLangCode = "oc"; break;
            case "ᐊᓂᔑᓈᐯᒧᐎᓐ" : newLangCode = "oj"; break;
            case "Afaan Oromoo" : newLangCode = "om"; break;
            case "ଓଡ଼ିଆ" : newLangCode = "or"; break;
            case "Ирон ӕвзаг" : newLangCode = "os"; break;
            case "ਪੰਜਾਬੀ" : newLangCode = "pa"; break;
            case "पािऴ" : newLangCode = "pi"; break;
            case "Polski" : newLangCode = "pl"; break;
            case "پښتو" : newLangCode = "ps"; break;
            case "Português" : newLangCode = "pt"; break;
            case "Runa Simi" : newLangCode = "qu"; break;
            case "Rumantsch grischun" : newLangCode = "rm"; break;
            case "kiRundi" : newLangCode = "rn"; break;
            case "Română" : newLangCode = "ro"; break;
            case "русский язык" : newLangCode = "ru"; break;
            case "Kinyarwanda" : newLangCode = "rw"; break;
            case "संस्कृतम्" : newLangCode = "sa"; break;
            case "sardu" : newLangCode = "sc"; break;
            case "सिन्धी" : newLangCode = "sd"; break;
            case "Davvisámegiella" : newLangCode = "se"; break;
            case "Yângâ tî sängö" : newLangCode = "sg"; break;
            case "srpskohrvatski jezik" : newLangCode = "sh"; break;
            case "සිංහල" : newLangCode = "si"; break;
            case "Slovenčina" : newLangCode = "sk"; break;
            case "Slovenščina" : newLangCode = "sl"; break;
            case "Gagana fa'a Samoa" : newLangCode = "sm"; break;
            case "chiShona" : newLangCode = "sn"; break;
            case "Soomaaliga" : newLangCode = "so"; break;
            case "Shqip" : newLangCode = "sq"; break;
            case "српски језик" : newLangCode = "sr"; break;
            case "SiSwati" : newLangCode = "ss"; break;
            case "seSotho" : newLangCode = "st"; break;
            case "Basa Sunda" : newLangCode = "su"; break;
            case "Svenska" : newLangCode = "sv"; break;
            case "Kiswahili" : newLangCode = "sw"; break;
            case "தமிழ்" : newLangCode = "ta"; break;
            case "తెలుగు" : newLangCode = "te"; break;
            case "тоҷикӣ" : newLangCode = "tg"; break;
            case "ไทย" : newLangCode = "th"; break;
            case "ትግርኛ" : newLangCode = "ti"; break;
            case "Türkmen" : newLangCode = "tk"; break;
            case "Tagalog" : newLangCode = "tl"; break;
            case "seTswana" : newLangCode = "tn"; break;
            case "faka Tonga" : newLangCode = "to"; break;
            case "Türkçe" : newLangCode = "tr"; break;
            case "xiTsonga" : newLangCode = "ts"; break;
            case "татарча" : newLangCode = "tt"; break;
            case "Twi" : newLangCode = "tw"; break;
            case "Reo Mā`ohi" : newLangCode = "ty"; break;
            case "Uyƣurqə" : newLangCode = "ug"; break;
            case "українська мова" : newLangCode = "uk"; break;
            case "اردو" : newLangCode = "ur"; break;
            case "O'zbek" : newLangCode = "uz"; break;
            case "tshiVenḓa" : newLangCode = "ve"; break;
            case "Tiếng Việt" : newLangCode = "vi"; break;
            case "Volapük" : newLangCode = "vo"; break;
            case "Walon" : newLangCode = "wa"; break;
            case "Wollof" : newLangCode = "wo"; break;
            case "isiXhosa" : newLangCode = "xh"; break;
            case "ייִדיש" : newLangCode = "yi"; break;
            case "Yorùbá" : newLangCode = "yo"; break;
            case "Saɯ cueŋƅ" : newLangCode = "za"; break;
            case "中文" : newLangCode = "zh"; break;
            case "isiZulu" : newLangCode = "zu"; break;
            default: newLangCode = Locale.getDefault().getLanguage(); break; }
        return newLangCode;
    }

}

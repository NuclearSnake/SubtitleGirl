package com.neoproductionco.subtitlegirl;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    private static File directory = new File(Environment.getExternalStorageDirectory().getPath(),"Neo Music");

    private enum ReadState {NUMBER, TIME, MAIN, GAP};
    //final String LOG_TAG = "myLogs";
    private static final int FILE_SELECT_CODE = 0;
    private static final int MY_DATA_CHECK_CODE = 741;
    private static long DEFAULT_LENGTH = 2*60*60*1000;
    private boolean DEFAULLT_INSTRUCTION_FILE = true;
    private long msec;
    private long next_sub = -1;
    private boolean neof = false;
    private ReadState readState;
    CountDownTimer UIUpdater = new CountDownTimer(120000, 500) {

        public void onTick(long millisUntilFinished) {
            updateUI();
        }

        public void onFinish() {
            UIUpdater.start();
        }
    };

    private void updateUI() {
        tvCurrent.setText(millisToString(msec));
        seekBar.setProgress((int)(msec/(DEFAULT_LENGTH/100)));
    }

    @Override
    protected void onDestroy() {
        tts.shutdown();
        super.onDestroy();
    }

    private CountDownTimer cdtimer = new CountDownTimer(120000,500) {
        @Override
        public void onTick(long millisUntilFinished) {
            msec += 500;
            checkSubs();
        }

        @Override
        public void onFinish() {
            cdtimer.start();
        }
    };

    private File file;
    BufferedReader breader;
    private TextToSpeech tts = null;

    Button btnChooseFile;
    Button btnPause;
    TextView btnReadNext;
    TextView tvCurrent;
    TextView tvMax;
    SeekBar seekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        readState = ReadState.NUMBER;

        seekBar = (SeekBar) findViewById(R.id.seekBar);
        btnReadNext = (TextView) findViewById(R.id.btnReadNext);
        tvCurrent = (TextView) findViewById(R.id.tvCurrent);
        tvMax = (TextView) findViewById(R.id.tvMax);
        btnChooseFile = (Button) findViewById(R.id.btnChooseFile);
        btnPause = (Button) findViewById(R.id.btnPause);

        btnReadNext.setOnClickListener(this);
        btnChooseFile.setOnClickListener(this);
        btnPause.setOnClickListener(this);
        seekBar.setOnSeekBarChangeListener(this);

        initFile(new InputStreamReader(getResources().openRawResource(R.raw.instruction)));
        tvMax.setText(millisToString(getMaxTime()));
        initFile(new InputStreamReader(getResources().openRawResource(R.raw.instruction)));

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        showFileChooser();
        return super.onOptionsItemSelected(item);
    }

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, getResources().getString(R.string.fileChooserPrompt)),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, getResources().getString(R.string.noFileChooserError),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void checkSubs(){
        if(!neof){
            UIUpdater.cancel();
            cdtimer.cancel();
            return;
        }

        while(neof && (next_sub == -1 || next_sub < msec)){
            readNext();
        }
    }

    private String loadNext(){
        //Log.d(LOG_TAG, "loadData");
        try {
            // catches IOException below

	 	       /* We have to use the openFileOutput()-method
	 	       * the ActivityContext provides, to
	 	       * protect your file from others and
	 	       * This is done for security-reasons.
	 	       * We chose MODE_WORLD_READABLE, because
	 	       *  we have nothing to hide in report file */

            //createDirectoryIfNotExists();
            //createFile();
            //osw.write("step "+traceStep+" :\n");
            //osw.write(lat+"\n");
            //isr.write(mChosenFile+"\n"+gd.currentSongNumer);
            String line;
            line = breader.readLine();
            if(line == null)
                return null;
            if(line.length() >= 0)
                return line;
            //traceStep++;
            //tvTraceStep.setText(traceStep+"");
        } catch (IOException ioe)
        {ioe.printStackTrace();}
        return null;
    }

    private long saveTime(String string){
        String[] time = string.split("[ ]");
        time = time[0].split("[:,]");
        long time_value;
        try{
            time_value = (((Integer.parseInt(time[0])*60 + Integer.parseInt(time[1]))*60) + Integer.parseInt(time[2]))*1000 + Integer.parseInt(time[3]);
        } catch (NumberFormatException nfe){
            time_value = -1;
        }
        return time_value;
    }

    private long getMaxTime(){
        String string;
        boolean worked = false;
        ReadState x = ReadState.NUMBER; // awaiting for a number
        long max_time = -1;

        do{
            string = loadNext();
            if(string == null) {
                DEFAULT_LENGTH = max_time;
                return max_time; // eof
            }

            if(string.length() == 0) {
                x = ReadState.NUMBER; // number goes after a gap
                continue;
            }

            string = string.trim();
            // a number - is the number of a subtitle, start of time or subtitle text
            if(string.charAt(0)>='0' && string.charAt(0)<='9')
                switch(x) {
                    case NUMBER:
                        x = ReadState.TIME;
                        continue; // ok, moving to the time
                    case MAIN:
                        continue; // no job here we just look for a time
                    case TIME:
                        max_time = saveTime(string); // saving the time
                        x = ReadState.MAIN;
                }

        } while(string != null); // it is an unnecessary guarantee that we are exiting the cycle
        DEFAULT_LENGTH = max_time;
        return max_time;
    }

    private void readNext(){
        String string;
        // The first line often contains anomalies, so...
        //if(string != null && string.length() != 0)
        //    string = String.valueOf(string.charAt(string.length()));
        ReadState x = ReadState.TIME; // awaiting for a time, as we just read the number

        do{
            if(msec < next_sub)
                return;
            string = loadNext();
            if(string == null) {
                neof = false; // eof
                return;
            }

            if(string.length() == 0) {
                x = ReadState.NUMBER; // number goes after a gap
                continue;
            }

            string = string.trim();
            // a number - is the number of a subtitle, start of time or subtitle text
            if(string.charAt(0)>='0' && string.charAt(0)<='9')
                switch(x) {
                    case NUMBER:
                        x = ReadState.TIME;
                        continue; // ok, moving to the time
                    case TIME:
                        next_sub = saveTime(string); // saving the time
                    case MAIN:
                        break; // no job here, dealing with it later
                }

            // we broke through the time of a subtitle & not too far (because a rewind could be)
            if(msec - next_sub < 1000 && msec > next_sub) {
                tts.speak(string, TextToSpeech.QUEUE_ADD, null);
                x = ReadState.MAIN;
            }

        } while(string != null); // it is an unnecessary guarantee that we are exiting the cycle
    }


    private void readNext2(){
        String string;
//        boolean isInSubtitle = true;
//        boolean wasInSubtitle = false;
        boolean worked = false;

        do{
            if(next_sub > msec)
                return;
            string = loadNext();
            if(string == null) {
                neof = false;
                return;
            }
            if(string.length() == 0) {
                if(readState == ReadState.MAIN)
                    worked = true;
                readState = ReadState.GAP;
                //isInSubtitle = false;
                continue;
            }
            if(string.charAt(0)>='0' && string.charAt(0)<='9'){
                //isInSubtitle = false;
                if(readState != ReadState.TIME) {
                    if (readState == ReadState.MAIN)
                        worked = true;

                    if (readState != ReadState.NUMBER)
                        readState = ReadState.NUMBER;
                    else {
                        readState = ReadState.TIME;
                        next_sub = saveTime(string);
                        if(worked)
                            return;
                    }

                    continue;
                }
            }

            if(string != null && (msec - next_sub < 2000 && msec > next_sub)) {
                tts.speak(string, TextToSpeech.QUEUE_ADD, null);
                readState = ReadState.MAIN;

//                if(!wasInSubtitle){
//                    wasInSubtitle = true;
//                    isInSubtitle = true;
//                }
            }

        } while(string != null);// && (wasInSubtitle == false || isInSubtitle == true));

    }

    public void initFile(){
        //file = new File(directory, "PlayerState.txt");
        neof = true;
        if(file == null)
            return;
        if(!file.exists())
            return;
        FileInputStream fIn = null;
        try {
            fIn = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //FileOutputStream fOut = openFileOutput("Report.txt", MODE_APPEND);
        try {
            breader = new BufferedReader(new InputStreamReader(fIn, "Cp1251"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        //breader = new BufferedReader(new InputStreamReader(fIn, "UTF-8"));
        loadNext(); // first line is often read with anomalies, so...
    }

    public void initFile(InputStreamReader isr){
        //file = new File(directory, "PlayerState.txt");
        neof = true;
        if(isr == null)
            return;
        //FileOutputStream fOut = openFileOutput("Report.txt", MODE_APPEND);
        breader = new BufferedReader(isr);
        //breader = new BufferedReader(new InputStreamReader(fIn, "UTF-8"));
        loadNext(); // first line is often read with anomalies, so...
    }

    public static String millisToString(long millis){
        StringBuilder x = new StringBuilder();
        int days = (int) (millis/(60*60*1000*24));
        millis -= days*60*60*1000*24;
        int hours = (int) (millis/(60*60*1000));
        millis -= hours*60*60*1000;
        int minutes = (int) (millis/(60*1000));
        millis -= minutes*60*1000;
        int seconds = (int) (millis/1000);
        millis -= seconds*1000;

        // days
        if(days > 0){
            x.append(days);
            x.append("d ");
            x.append(hours);
            x.append("h ");
            x.append(minutes);
            x.append("m ");
            x.append(seconds);
            x.append("s");
            return x.toString();
        }
        // hours
        if(hours > 0){
            x.append(hours);
            x.append("h ");
            x.append(minutes);
            x.append("m ");
            x.append(seconds);
            x.append("s");
            return x.toString();
        }
        // minutes
        if(minutes > 0){
            x.append(minutes);
            x.append("m ");
            x.append(seconds);
            x.append("s");
            return x.toString();
        }
        // seconds
        if(seconds > 0){
            x.append(seconds);
            x.append("s");
            x.append(millis);
            x.append("ms");
            return x.toString();
        }
        // milliseconds only
        if(millis < 1000){
            x.append(millis);
            x.append("ms");
            return x.toString();
        }

        // else - error
        return "N/D";
    }

    public static String getPath(Context context, Uri uri) throws URISyntaxException {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = { "_data" };
            Cursor cursor = null;

            try {
                cursor = context.getContentResolver().query(uri, projection, null, null, null);
                int column_index = cursor.getColumnIndexOrThrow("_data");
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                // Eat it
            }
        }
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    //Log.d(LOG_TAG, "File Uri: " + uri.toString());
                    // Get the path
                    String path = null;
                    try {
                        path = getPath(this, uri);
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    //FileUtils.getPath(this, uri);
                    //Log.d(LOG_TAG, "File Path: " + path);
                    // Get the file instance
                    // File file = new File(path);
                    // Initiate the upload
                    file = new File(path);

                    initFile();
                    tvMax.setText(millisToString(getMaxTime()));
                    initFile();
                }
                break;
            case MY_DATA_CHECK_CODE:
                if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                    // success, create the TTS instance
                    tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            if (status != TextToSpeech.SUCCESS) {
                                Toast.makeText(MainActivity.this, getResources().getString(R.string.ttsError), Toast.LENGTH_LONG).show();
                                return;
                            }
                            //Locale locale = new Locale("ru");
                            //int result = tts.setLanguage(locale);
                            int result = tts.setLanguage(Locale.getDefault());
                            //int result = tts.setLanguage(Locale.US);
                            if(result == TextToSpeech.LANG_MISSING_DATA)

                                Toast.makeText(MainActivity.this, getResources().getString(R.string.ttsMissesData), Toast.LENGTH_LONG).show();
                            else if(result == TextToSpeech.LANG_NOT_SUPPORTED)
                                Toast.makeText(MainActivity.this, getResources().getString(R.string.ttsDoesNotSupport), Toast.LENGTH_LONG).show();
                            Toast.makeText(MainActivity.this, getResources().getString(R.string.ttsReady), Toast.LENGTH_SHORT).show();
                        }
                    });

                    //changeLanguage();
                } else {
                    // missing data, install it
                    Intent installIntent = new Intent();
                    installIntent.setAction(
                            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(installIntent);
                }
            break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if(fromUser){
            tvCurrent.setText(millisToString(progress*DEFAULT_LENGTH/100));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        cdtimer.cancel();
        next_sub = -1;
        if(DEFAULLT_INSTRUCTION_FILE)
            initFile(new InputStreamReader(getResources().openRawResource(R.raw.instruction)));
        else
            initFile();
        msec = seekBar.getProgress()*DEFAULT_LENGTH/100;
        cdtimer.start();
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
//            case R.id.btnReadNext:
//                readNext();
//                break;
            case R.id.btnChooseFile:
                showFileChooser();
                break;
            case R.id.btnPause:
                if(btnPause.getText().equals(getResources().getString(R.string.btnPauseText))){
                    btnPause.setText(getResources().getString(R.string.btnPlayText));
                    cdtimer.cancel();
                } else {
                    btnPause.setText(getResources().getString(R.string.btnPauseText));
                    cdtimer.start();
                    UIUpdater.start();
                }
                break;
        }
    }


}
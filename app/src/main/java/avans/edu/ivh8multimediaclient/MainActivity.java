package avans.edu.ivh8multimediaclient;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

public class MainActivity extends AppCompatActivity implements Client.VideoScreen {
    private Button buttonSetup, buttonPlay, buttonPause, buttonTear;
    private ImageView frame;
    private ProgressBar progressBar;
    private Client client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonSetup = (Button)findViewById(R.id.btnSetup);
        buttonPlay = (Button)findViewById(R.id.btnPlay);
        buttonPause = (Button)findViewById(R.id.btnPause);
        buttonTear = (Button)findViewById(R.id.btnTear);
        frame = (ImageView)findViewById(R.id.frame);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.getProgressDrawable().setColorFilter(
                Color.RED, android.graphics.PorterDuff.Mode.SRC_IN);

        buttonSetup.setOnClickListener(new SetupListener());
        buttonPlay.setOnClickListener(new PlayListener());
        buttonPause.setOnClickListener(new PauseListener());
        buttonTear.setOnClickListener(new TearDownListener());

        client = new Client(this);
    }

    @Override
    public void drawFrame(Bitmap bitmap) {
        Log.d("VIVZ", "drawFrame() mainActivity called!");
        frame.setImageBitmap(bitmap);
    }


    @Override
    public void enableSetupBtn() {
        buttonSetup.setEnabled(true);
    }

    @Override
    public void enableControlBtns() {
        buttonPlay.setEnabled(true);
        buttonPause.setEnabled(true);
        buttonTear.setEnabled(true);
    }

    @Override
    public void disableSetupBtn() {
        buttonSetup.setEnabled(false);
    }

    @Override
    public void updateProgressBar(int progress) {
        progressBar.setProgress(progress);
    }

    private class SetupListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            client.handleSetupRequest();
        }
    }

    private class PlayListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            client.handlePlayRequest();
        }
    }

    private class PauseListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            client.handlerPauseRequest();
        }
    }

    private class TearDownListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            client.handlerTearDownRequest();
        }
    }
}
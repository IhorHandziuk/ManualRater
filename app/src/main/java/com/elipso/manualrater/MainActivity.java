package com.elipso.manualrater;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.view.MotionEventCompat;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.UUID;

public class MainActivity extends Activity {

    private static class NamedBitmap {
        String id;
        Bitmap bitmap;

        NamedBitmap(String id, Bitmap bitmap) {
            this.id = id;
            this.bitmap = bitmap;
        }
    }

    private static class Point {
        float x, y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }

        double distanceTo(Point o) {
            return Math.sqrt(Math.pow((o.x - x), 2) + Math.pow((o.y - y), 2));
        }
    }

    ImageView imageView;
    TextView countView, uniqueIdView, ratingView;

    Point startPoint;
    String uniqueID;
    int verticalResolution, horizontalResolution;
    Queue<NamedBitmap> imageQueue = new LinkedList<>();

    private static final int BUFFER_SIZE = 8;
    private static final int IT_IS_NOT_SWIPE = -2;
    private static final int MOVE_TO_SPECIAL_FOLDER = -1;

    private static final String HOST_URL = "http://manual-rate.jls-sto1.elastx.net";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        for (int i = 0; i < BUFFER_SIZE; i++) {
            addNextFileToQueue();
        }

        imageView = findViewById(R.id.image_place_holder);
        uniqueIdView = findViewById(R.id.uniqueIdView);
        countView = findViewById(R.id.countView);
        ratingView = findViewById(R.id.ratingTextView);

        uniqueID = getUniqueID();
        uniqueIdView.setText(uniqueID);
        initRatedImageCount();
        countView.setText(getRatedImageCount());

        setInitialBitmap();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        verticalResolution = metrics.heightPixels;
        horizontalResolution = metrics.widthPixels;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (imageQueue.size() > 1) {
            int action = MotionEventCompat.getActionMasked(event);
            float x = event.getX() / horizontalResolution;
            float y = event.getY() / verticalResolution;

            switch (action) {
                case (MotionEvent.ACTION_DOWN):
                    startPoint = new Point(x, y);
                    return true;
                case (MotionEvent.ACTION_UP):
                    int val = onTouchingScreen(x, y);
                    if (val != IT_IS_NOT_SWIPE) {
                        postRating(imageQueue.peek().id, Integer.toString(val), uniqueID);
                        imageQueue.poll();
                        addNextFileToQueue();
                        imageView.setImageBitmap(imageQueue.peek().bitmap);
                        countView.setText(incrementRatedImageCount());
                    }
                    return true;
                default:
                    // finger is on display
                    onTouchingScreen(x, y);
                    return true;
            }
        }
        return true;
    }

    int onTouchingScreen(float x, float y) {
        Point endPoint = new Point(x, y);
        int res = valueOfVector(startPoint, endPoint);
        if (startPoint.distanceTo(endPoint) > 0.14 && startPoint.y > 0.3) {
            ratingView.setText((Integer.valueOf(res).toString()));
            return res;
        } else {
            return IT_IS_NOT_SWIPE;
        }
    }

    /**
     * Returns number from 0 to 100 (rating)
     * or MOVE_TO_SPECIAL_FOLDER constant in case of downward direction
     */
    int valueOfVector(Point start, Point end) {
        // Angle range is (0, 200) clockwise.
        // 0 to 100 in upper half; from 100 to 200 in lower half.
        double angle = 100 - Math.atan2(start.y - end.y, end.x - start.x) * 100 / Math.PI;

        if (0 <= angle&& angle <= 100) {
            return (int)angle;
        } else if (100 < angle && angle <= 120) {
            return 100;
        } else if (180 < angle && angle <= 199) {
            return 0;
        } else return MOVE_TO_SPECIAL_FOLDER;
    }

    void setInitialBitmap() {
        new AsyncTask<Void, Void, String>(){
            @Override
            protected String doInBackground(Void[] params) {
                while (imageQueue.isEmpty()) {
                    SystemClock.sleep(100);
                }
                return "NOTIFY_SUCCESS";
            }

            @Override
            protected void onPostExecute(String s) {
                if (imageView.getDrawable() == null) {
                    imageView.setImageBitmap(imageQueue.peek().bitmap);
                }
            }
        }.execute();
    }

    void addNextFileToQueue() {
        new AsyncTask<Void, Void, String>(){
            @Override
            protected String doInBackground(Void[] params) {
                boolean success = false;
                while (!success) {
                    try {
                        String id = new HttpRequest(HOST_URL + "/id")
                                .prepare()
                                .sendAndReadString();
                        byte[] file = new HttpRequest(HOST_URL + "/image?id=" + id)
                                .prepare()
                                .sendAndReadBytes();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(file, 0, file.length);
                        imageQueue.add(new NamedBitmap(id, bitmap));
                        success = true;
                    } catch (IOException e) {
                        SystemClock.sleep(500);
                    }
                }
                return "GET_SUCCESS";
            }
        }.execute();
    }

    void postRating(final String id, final String rating, final String user) {
        new AsyncTask<Void, Void, String>(){

            @Override
            protected String doInBackground(Void[] params) {
                boolean success = false;
                while (!success) {
                    try {
                        HttpRequest req = new HttpRequest(HOST_URL + "/addRating");
                        req.prepare(HttpRequest.Method.POST)
                                .withData("id=" + id + "&" +
                                        "rating=" + rating + "&" +
                                        "user=" + user)
                                .send();
                        success = true;
                    } catch (IOException e) {
                        SystemClock.sleep(500);
                    }
                }
                return "POST_SUCCESS";
            }
        }.execute();
    }

    String getUniqueID() {
        String res = "";
        File file = new File(getApplicationContext().getFilesDir(), "unique");
        if (!file.exists()) {
            String uniqueID = UUID.randomUUID().toString();
            try {
                FileOutputStream stream = new FileOutputStream(file);
                try {
                    stream.write(uniqueID.getBytes());
                } finally {
                    stream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            Scanner scanner = new Scanner(file);
            res = scanner.next().substring(0, 8);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return res;
    }

    void initRatedImageCount() {
        File file = new File(getApplicationContext().getFilesDir(), "count");
        if (!file.exists()) {
            String initialVal = "0";
            try {
                FileOutputStream stream = new FileOutputStream(file);
                try {
                    stream.write(initialVal.getBytes());
                } finally {
                    stream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    String getRatedImageCount() {
        int res = 0;
        File file = new File(getApplicationContext().getFilesDir(), "count");
        try {
            Scanner scanner = new Scanner(file);
            res = scanner.nextInt();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return Integer.toString(res);
    }

    String incrementRatedImageCount() {
        int res = 0;
        File file = new File(getApplicationContext().getFilesDir(), "count");
        try {
            Scanner scanner = new Scanner(file);
            res = scanner.nextInt() + 1;
            String textToWrite = Integer.toString(res);
            PrintWriter writer = new PrintWriter(file);
            writer.print(textToWrite);
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return Integer.toString(res);
    }
}

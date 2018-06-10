package org.tensorflow.demo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

import org.opencv.android.JavaCameraView;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Opencvtest extends Activity implements CvCameraViewListener2 {

    // Used for logging success or failure messages
    private static final String TAG = "OCVSample::Activity";

    private Classifier classifier;

    private static final int INPUT_SIZE = 32;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final String INPUT_NAME = "features";
    private static final String OUTPUT_NAME = "y_pred";

    private static final String MODEL_FILE = "file:///android_asset/sign_recognizer_optimized.pb";
    private static final String LABEL_FILE = "file:///android_asset/labels.txt";
    private static final Size minKSize = new Size(10.0, 10.0);
    private static final Size maxKSize = new Size(500.0, 500.0);
    private static final int scale = 2;
    private static final Size sz = new Size(32,32);

    // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
    private CameraBridgeViewBase mOpenCvCameraView;

    Mat mRgba;
    Mat mHsv;
    Mat mHsvFiltered;
    Mat mReduced;
    Mat mGray;

    Scalar mLowerWhite;
    Scalar mUpperWhite;

    Mat mLowerRedRange;
    Mat mUpperRedRange;
    Mat mWhiteRange;
    Mat mMask;
    Mat mRedRange;
//    Mat mRgbaT;
//    Mat mRgbaF;
//    Mat mRgbaP;

    Scalar SIGN_RECT_COLOR = new Scalar(0, 255, 0, 255);

    private File                  mCascadeFile;
    private CascadeClassifier     mJavaDetector;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.haar_cascade);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "haar_cascade.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();
                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        mJavaDetector.load(mCascadeFile.getAbsolutePath());

                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.show_camera);

        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.show_camera_activity_java_surface_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        classifier =
                TensorFlowImageClassifier.create(
                        getAssets(),
                        MODEL_FILE,
                        LABEL_FILE,
                        INPUT_SIZE,
                        IMAGE_MEAN,
                        IMAGE_STD,
                        INPUT_NAME,
                        OUTPUT_NAME);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        mHsv = new Mat();
//        mHsvFiltered = new Mat();
        mReduced = new Mat();
        mGray = new Mat();

        mLowerRedRange = new Mat();
        mUpperRedRange = new Mat();
        mRedRange = new Mat();
        mWhiteRange = new Mat();
        mMask = new Mat();

        Core.inRange(mHsv, new Scalar(0, 70, 50), new Scalar(10,255,255),
                mLowerRedRange);
        Core.inRange(mHsv, new Scalar(170,70,50), new Scalar(180,255,255),
                mUpperRedRange);
        Core.inRange(mHsv, new Scalar(0,0,50), new Scalar(0,0,255),
                mWhiteRange);
    }

    public void onCameraViewStopped() {
    }

    public static int unsignedToBytes(byte b) {
        return b & 0xFF;
    }

    public static org.opencv.core.Rect getSignRoi(Mat orig, int x, int y, int radius, int padding) {
        int paddedRadius;
        int paddedDiameter;
        int upLeftX;
        int upLeftY;
        int xLimit;
        int yLimit;

        while (padding >= 0) {
            paddedRadius = radius + padding;
            upLeftX = x - paddedRadius;
            upLeftY = y - paddedRadius;
            xLimit = x + paddedRadius;
            yLimit = y + paddedRadius;
            if ((upLeftX >= 0 && upLeftY >= 0 && xLimit < orig.cols() && yLimit < orig.rows())) {
                paddedDiameter = paddedRadius * 2;
                return new org.opencv.core.Rect(upLeftX, upLeftY, paddedDiameter, paddedDiameter);
            }
            padding--;
        }
        return null;
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
//        Core.transpose(mRgba, mRgbaT);
//        Core.flip(mRgbaT, mRgbaF, 1 );

//        recordFrame(mRgbaF, "frames", mRgbaF.toString() + "frame.jpg");

//        MatOfRect signs = new MatOfRect();

//        Size reducedSize = new Size(mRgba.width() / scale,mRgba.height() / scale);
//        Mat scaled = new Mat();
//        Imgproc.resize( mRgba, scaled, reducedSize );

//        org.opencv.core.Rect roi = new org.opencv.core.Rect((mRgba.cols() / scale) * (scale - 1),
//                0, mRgba.cols() / scale, mRgba.rows() / scale);
//        int xOff = (mRgba.cols() / scale) * (scale - 1);
        int xOff = 0;
//        org.opencv.core.Rect roi = new org.opencv.core.Rect(xOff, 0,
//                mRgba.cols() / scale, mRgba.rows());
        org.opencv.core.Rect roi = new org.opencv.core.Rect(xOff, 0,
                mRgba.cols(), mRgba.rows() / scale);
//        Imgproc.line(mRgba, new Point((double)xOff, 0), new Point((double)xOff, mRgba.rows()), SIGN_RECT_COLOR);
        Mat scaled = new Mat(mRgba, roi);
//        Imgproc.resize(scaled, scaled, scaled.size(),0.5, 0.5, Imgproc.INTER_LINEAR);
//        Core.bitwise_not(scaled, mHsvInv);

        Imgproc.cvtColor(scaled, mHsv, Imgproc.COLOR_BGR2HSV_FULL);

//        Log.i(TAG, "height = " + mHsv.rows());
//        Log.i(TAG, "width = " + mHsv.cols());
//        recordFrame(mHsv, "frames", Calendar.getInstance().getTime() + "_res_frame.jpg");

        Core.bitwise_or(mRedRange, mWhiteRange, mMask);
        mHsvFiltered = new Mat();
        Core.bitwise_and(mHsv, mHsv, mHsvFiltered, mRedRange);

//        recordFrame(mHsvFiltered, "frames", Calendar.getInstance().getTime() + "_res_frame.jpg");

        Size reducedSize = new Size(mHsvFiltered.cols() / scale,mHsvFiltered.rows() / scale);

        Imgproc.resize(mHsvFiltered, mReduced, reducedSize);
//        recordFrame(mReduced, "frames", Calendar.getInstance().getTime() + "_resized_frame.jpg");

//        Mat res2 = mReduced.clone();
//        byte[] blackPixel = new byte[]{0, 0, 0};
//        byte[] whitePixel = new byte[]{(byte) 255, (byte) 255, (byte) 255};
//        byte[] pixel = new byte[3];
//        byte[] lr = scalarToArray(mLowerRed, 3);
//        byte[] ur = scalarToArray(mUpperRed, 3);
//        byte[] lw = scalarToArray(mLowerWhite, 3);
//        byte[] uw = scalarToArray(mUpperWhite, 3);
//        boolean inRedRange;
//        boolean inWhiteRange;
//        ArrayList<byte[]> neighbors;

//        for (int y = 0; y < mReduced.rows() / 16; y++) {
//            for (int x = 0; x < mReduced.cols(); x++){
//                mReduced.get(y, x, pixel);
//                res2.put(y, x, blackPixel);
//                inRedRange = maxValue(arrSubstract(lr, pixel, pixel.length)) < 0 &&
//                        maxValue(arrSubstract(pixel, ur, pixel.length)) < 0;
//                if (inRedRange) {
//                    neighbors = getPixelNeighbors(y, x, mReduced);
//                    for (int n = 0; n < neighbors.size(); n++){
//                        byte[] neighbor = neighbors.get(n);
//                        inWhiteRange = maxValue(arrSubstract(lw, neighbor, neighbor.length)) < 0 &&
//                                maxValue(arrSubstract(neighbor, uw, neighbor.length)) < 0;
//                        if (inWhiteRange) {
//                            res2.put(y, x, whitePixel);
//                            break;
//                        }
//                    }
//                }
//            }
//
//        }
//        recordFrame(res2, "frames", Calendar.getInstance().getTime() + "_res2_frame.jpg");

        Imgproc.cvtColor(mReduced, mGray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.morphologyEx(mGray, mGray, Imgproc.MORPH_OPEN,
                Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5)));

//        recordFrame(mGray, "frames", Calendar.getInstance().getTime() + "_dilated_frame.jpg");

        Mat bilateralFiltered = new Mat();
        Imgproc.bilateralFilter(mGray, bilateralFiltered, 5, 175, 175);
//        recordFrame(bilateralFiltered, "frames", Calendar.getInstance().getTime() + "_res_frame.jpg");

        Mat circles = new Mat();
//        Imgproc.HoughCircles(bilateralFiltered, circles, Imgproc.HOUGH_GRADIENT, 1, 20,
//                50, 30, 1, 50);
        Imgproc.HoughCircles(bilateralFiltered, circles, Imgproc.HOUGH_GRADIENT, 2, 100,
                35, 20, 1, 50);

        Point circleCenter = new Point();
        for(int i = 0; i < circles.cols(); i++) {
            double[] circle = circles.get(0, i);
            double[] centerCoord = {Math.round(circle[0]) * scale + xOff, Math.round(circle[1]) * scale};
            circleCenter.set(centerCoord);
            int radius = (int) (Math.round(circle[2]) * scale);
//            Imgproc.circle(mRgba, circleCenter, radius, new Scalar(0,255,0), 4);

            org.opencv.core.Rect signRoi = getSignRoi(mRgba, (int)circleCenter.x, (int)circleCenter.y, radius, 15);
            if (signRoi == null) {
                continue;
            }

            Mat croppedSign = new Mat(mRgba, signRoi);
            Mat resizedSign = new Mat();
            Imgproc.resize(croppedSign, resizedSign, sz);
            Bitmap resizedBmp = Bitmap.createBitmap(resizedSign.width(), resizedSign.height(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(resizedSign, resizedBmp);
            final List<Classifier.Recognition> results = classifier.recognizeImage(resizedBmp);
//            Log.i(TAG, "results = " + results.toString());
            Point topLeft = new Point(signRoi.x, signRoi.y);
            Point bottomRight = new Point(signRoi.x + signRoi.width, signRoi.y + signRoi.height);
            if (results.isEmpty())
                continue;
            Imgproc.rectangle(mRgba, topLeft, bottomRight, SIGN_RECT_COLOR, 3);
            Imgproc.putText(mRgba, results.toString(), new Point(topLeft.x, bottomRight.y + 15),
                    Core.FONT_HERSHEY_COMPLEX, 1, new Scalar(0, 255, 0));
        }

//        if (mJavaDetector != null) {
//            mJavaDetector.detectMultiScale(scaled, signs, 1.1, 10, 1,
//                    minKSize, maxKSize);
//        } else {
//            Log.e(TAG, "Detection method is not selected!");
//        }

//        org.opencv.core.Rect[] signsArray = signs.toArray();
//        Mat cropped;
//        Mat resized;
//
//        for (int i = 0; i < signsArray.length; i++) {
//            cropped = new Mat(scaled, signsArray[i]);
//            resized = new Mat();
//            Imgproc.resize( cropped, resized, sz );
//            Bitmap resizedBmp = Bitmap.createBitmap(resized.width(), resized.height(), Bitmap.Config.RGB_565);
//            Utils.matToBitmap(resized, resizedBmp);
//            final List<Classifier.Recognition> results = classifier.recognizeImage(resizedBmp);
//            Log.i(TAG, "results = " + results.toString());
//            Point topLeft = new Point(signsArray[i].tl().x * scale, signsArray[i].tl().y * scale);
//            Point bottomRight = new Point(signsArray[i].br().x * scale, signsArray[i].br().y * scale);
//            Imgproc.rectangle(mRgba, topLeft, bottomRight, SIGN_RECT_COLOR, 3);
//            Imgproc.putText(mRgba, results.toString(), new Point(topLeft.x, bottomRight.y + 30),
//                    Core.FONT_HERSHEY_COMPLEX, 1, new Scalar(0, 255, 0));
//        }
//        Core.transpose(mRgba, mRgbaT);
//        Imgproc.resize(mRgbaT, mRgbaP, new Size(mRgbaP.width(), mRgbaP.height()), 0,0, 0);
//        Core.flip(mRgbaP, mRgba, 1 );
        return mRgba; // This function must return
    }

    public void recordFrame(Mat frame, String dirname, String filename) {
        Bitmap bmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(frame, bmp);
        FileOutputStream out = null;

        File sd = new File(Environment.getExternalStorageDirectory() + "/" + dirname);
        boolean success = true;
        if (!sd.exists()) {
            success = sd.mkdir();
        }
        if (success) {
            File dest = new File(sd, filename);

            try {
                out = new FileOutputStream(dest);
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, out); // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored

            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, e.getMessage());
            } finally {
                try {
                    if (out != null) {
                        out.close();
                        Log.d(TAG, "OK!!");
                    }
                } catch (IOException e) {
                    Log.d(TAG, e.getMessage() + "Error");
                    e.printStackTrace();
                }
            }
        }
    }


    public ArrayList<byte[]> getPixelNeighbors(int y, int x, Mat frame) {
        ArrayList<byte[]> neighbors = new ArrayList<byte[]>();
        byte[] pixel = new byte[3];

        if (x > 0) {
            frame.get(y, x - 1, pixel);
            neighbors.add(pixel);
        }
        if (y > 0) {
            frame.get(y - 1, x, pixel);
            neighbors.add(pixel);
        }
        if (x < frame.cols() - 1) {
            frame.get(y, x + 1, pixel);
            neighbors.add(pixel);
        }
        if (y < frame.rows() - 1) {
            frame.get(y + 1, x, pixel);
            neighbors.add(pixel);
        }
        if (x > 0 && y > 0) {
            frame.get(y - 1, x - 1, pixel);
            neighbors.add(pixel);
        }
        if ((x < frame.cols() - 1) && y > 0) {
            frame.get(y - 1, x + 1, pixel);
            neighbors.add(pixel);
        }
        if ((x < frame.cols() - 1) && (y < frame.rows() - 1)) {
            frame.get(y + 1, x + 1, pixel);
            neighbors.add(pixel);
        }
        if (x > 0 && (y < frame.rows() - 1)) {
            frame.get(y + 1, x - 1, pixel);
            neighbors.add(pixel);
        }
        return neighbors;
    }

    public byte[] scalarToArray(Scalar src, int length) {
        byte[] result = new byte[length];

        for (int i = 0; i < length; i++) {
            result[i] = (byte) src.val[i];
        }
        return  result;
    }

    public int[] arrSubstract(byte[] minuend, byte[] subtrahend, int length) {
        int[] result = new int[length];

        for (int i = 0; i < length; i++) {
            result[i] = unsignedToBytes(minuend[i]) - unsignedToBytes(subtrahend[i]);
        }
        return result;
    }

    public int maxValue(int[] arr) {
        int max = arr[0];

        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > max) {
                max = arr[i];
            }
        }
        return max;
    }
}

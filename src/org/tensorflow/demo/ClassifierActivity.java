/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.widget.TextView;

import org.tensorflow.demo.OverlayView.DrawCallback;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.util.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.model.Recognition;

import java.util.List;
import java.util.Vector;
import static org.tensorflow.demo.Config.LOGGING_TAG;
import static org.tensorflow.demo.Config.INPUT_SIZE;
import static org.tensorflow.demo.Config.LOGGING_TAG;

public class ClassifierActivity extends CameraActivity implements OnImageAvailableListener {
  private boolean MAINTAIN_ASPECT = true;
  private float TEXT_SIZE_DIP = 10;

  private TensorFlowImageRecognizer recognizer;
  private Integer sensorOrientation;
  private int previewWidth = 0;
  private int previewHeight = 0;
  private Bitmap croppedBitmap = null;
  private boolean computing = false;
  private Matrix frameToCropTransform;

  private OverlayView overlayView;
  private BorderedText borderedText;
  private long lastProcessingTimeMs;

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
            TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    recognizer = TensorFlowImageRecognizer.create(getAssets());

    overlayView = (OverlayView) findViewById(R.id.overlay);
    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    final int screenOrientation = getWindowManager().getDefaultDisplay().getRotation();

    Log.i(LOGGING_TAG, String.format("Sensor orientation: %d, Screen orientation: %d",
            rotation, screenOrientation));

    sensorOrientation = rotation + screenOrientation;

    Log.i(LOGGING_TAG, String.format("Initializing at size %dx%d", previewWidth, previewHeight));

    croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

    frameToCropTransform = ImageUtils.getTransformationMatrix(previewWidth, previewHeight,
            INPUT_SIZE, INPUT_SIZE, sensorOrientation, MAINTAIN_ASPECT);
    frameToCropTransform.invert(new Matrix());

    addCallback((final Canvas canvas) -> renderAdditionalInformation(canvas));
  }

  @Override
  public void onImageAvailable(final ImageReader reader) {
    Image image = null;

    try {
      image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (computing || !istart) {
        image.close();
        return;
      }

      computing = true;
      fillCroppedBitmap(image);
      image.close();
    } catch (final Exception ex) {
      if (image != null) {
        image.close();
      }
      Log.e(LOGGING_TAG, ex.getMessage());
    }

    runInBackground(() -> {
      final long startTime = SystemClock.uptimeMillis();
      final List<Recognition> results = recognizer.recognizeImage(croppedBitmap);
      lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
      overlayView.setResults(results);
      //speak(results);
      requestRender();
      computing = false;
    });
  }

  private void fillCroppedBitmap(final Image image) {
    Bitmap rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    rgbFrameBitmap.setPixels(ImageUtils.convertYUVToARGB(image, previewWidth, previewHeight),
            0, previewWidth, 0, 0, previewWidth, previewHeight);
    new Canvas(croppedBitmap).drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (recognizer != null) {
      recognizer.close();
    }
  }

  private void renderAdditionalInformation(final Canvas canvas) {
    final Vector<String> lines = new Vector();
    if (recognizer != null) {
      for (String line : recognizer.getStatString().split("\n")) {
        lines.add(line);
      }
    }

    lines.add("Frame: " + previewWidth + "x" + previewHeight);
    lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
    lines.add("Rotation: " + sensorOrientation);
    lines.add("Inference time: " + lastProcessingTimeMs + "ms");

    borderedText.drawLines(canvas, 10, 10, lines);
  }
}

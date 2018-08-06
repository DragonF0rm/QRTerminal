package com.example.androidstudio.qrterminal;


import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    //Примечание: так как активность есть класс, то объекты тоже являются полями, только с более сложной структурой
    //Примечание: в пределах класса поля можно рассматривать как глобальные переменные

    /* Секция объявления полей */

    private TextureView preview;
    private Button showListButton;
    private ImageButton roundButton;

    private static final int REQUEST_CAMERA_PERMISSION_CODE = 200;
    private final String TAG = "Main Activity";
    private Size imageDimention;
    private String cameraId;
    private String[] cameraIdList;
    private int camera = 0;
    private CameraManager manager;
    private CameraDevice cameraDevice;
    private boolean has_it_start_yet = true;

    /* Секция объявления объектов  */

    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        //Описываем textureListener - объект, проводящий изменения над preview
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            //Начинаем трансляцию превью
            if(!has_it_start_yet) {
                has_it_start_yet = true;
                openCamera();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            //Цитата из офф. документации:
            //Ignored, Camera does all the work for us
            //Примечание: речь идёт о старом API, так что х.з.
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            //Заканчиваем трансляцию превью
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            //Вызывается с частотой обновления превью камеры
            //Здесь поместить распознование QR-кода?
            Log.i("Testing","QR Detection");
            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            int side = getResources().getDimensionPixelSize(R.dimen.spaceSide);
            Bitmap code = Bitmap.createBitmap(preview.getBitmap(),(size.x-side)/2,(size.y-side)/2,side,side);
            BarcodeDetector detector = new BarcodeDetector.Builder(getApplicationContext())
                    .setBarcodeFormats(Barcode.QR_CODE)
                    .build();
            if(!detector.isOperational()){
                Log.i("BarcodeDetector","Detector is not operational");
            }
            else{
                Frame frame = new Frame.Builder().setBitmap(code).build();
                SparseArray<Barcode> barcodes = detector.detect(frame);
                Log.i("BarcodeDetector","Detector has found "+Integer.toString(barcodes.size())+" codes");
                Toast.makeText(getApplicationContext(),Integer.toString(barcodes.size())+" QR codes detected",Toast.LENGTH_LONG);

            }
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //Метод вызывается после manager.openCamera();
            Log.i(TAG, "Camera's state is onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.i(TAG, "Camera's state is onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Log.e(TAG, "Camera's state is onError");

        }
    };

    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()){
                case R.id.showListButton:{
                    break;
                }
                case R.id.roundButton:{
                    changeCamera();
                    break;
                }
            }
        }
    };

    /* Секция объявления методов */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.camera_preview);
        preview.setSurfaceTextureListener(textureListener);//Готовим элемент для превью
        showListButton = findViewById(R.id.showListButton);
        showListButton.setOnClickListener(onClickListener);
        roundButton = findViewById(R.id.roundButton);
        roundButton.setOnClickListener(onClickListener);

        manager = (CameraManager) this.getSystemService(CAMERA_SERVICE);
        try {
            cameraIdList = manager.getCameraIdList();//Получаем массив id камер
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Проверяем наличие необходимых прав
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //Прав недостаточно, запрашиваем дополнительные
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA_PERMISSION_CODE);
            return;
        }
        if(preview.isAvailable()){
            openCamera();
        }
        else{
            has_it_start_yet = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraDevice!=null) {
            closeCamera();
        }
    }

    private void changeCamera(){
        closeCamera();
        camera++;//При смене камеры должна включится следующая по счёту
        if(camera == 2) {
            camera = 0;//Избегаем выхода за пределы массива
            roundButton.setImageResource(R.mipmap.camera_front_round);
        }
        else{
            roundButton.setImageResource(R.mipmap.camera_back_round);
        }
        if(preview.isAvailable()){
            openCamera();
        }
        else{
            has_it_start_yet = false;
        }
    }

    private void openCamera() {
        Log.i(TAG, "Камера открывается");
        try {
            //Проверяем наличие необходимых прав
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                //Прав недостаточно, запрашиваем дополнительные
                ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA_PERMISSION_CODE);
                return;
            }
            cameraId = cameraIdList[camera];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map !=null;
            imageDimention = map.getOutputSizes(SurfaceTexture.class)[0];//Получаем размеры изображения
            manager.openCamera(cameraId, stateCallback, null);
        }
        catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    protected void createCameraPreview(){
        Log.i(TAG,"Camera preview is on create");
        try{
            SurfaceTexture texture = preview.getSurfaceTexture();
            assert texture !=null;
            texture.setDefaultBufferSize(imageDimention.getWidth(),imageDimention.getHeight());
            Surface surface = new Surface(texture);
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice==null){
                        //The camera has already closed
                        return;
                    }
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),null,null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG,"CameraCaptureSession cinfiguration failed");
                }
            }, null);

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void closeCamera(){
        cameraDevice.close();
        cameraDevice=null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //Выше были запрошены права, результат обработки запроса переопределяется здесь
        if(requestCode==REQUEST_CAMERA_PERMISSION_CODE){
            //Если это наш запрос
            if(grantResults[0]==PackageManager.PERMISSION_DENIED){
                //Если права не были получены
                Toast.makeText(MainActivity.this,"Приложение не может продолжить работу без получения запрошенных прав",Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}

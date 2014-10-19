package com.techlabxe.mikuboard;

import java.io.*;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardView.StereoRenderer;
import com.google.vrtoolkit.cardboard.*;

interface FileDialogListener {
	public void onClick( String selectedFile );
}
class FileDialog implements OnClickListener {
	FileDialog( Context ctx, String title, String baseDir, String ext, FileDialogListener listener ) {
        StringBuilder filePath = new StringBuilder(baseDir);
        
		ArrayList<String> filelist = new ArrayList<String>();
		for (File f : new File(filePath.toString()).listFiles() ) {
			if (f.isFile() && f.getName().endsWith( ext )) {
				filelist.add( f.getName() );
			}
		}
		mBasePath = filePath.toString();
		mListener = listener;
		mFileList = filelist.toArray( new String[0] );
    	AlertDialog.Builder alert = new AlertDialog.Builder(ctx)
        	.setTitle(title)
        	.setItems(mFileList, this );
    	alert.setCancelable(false);
    	mDialog = alert.create();		
	}
	public void show() {
		mDialog.show();
	}
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if( mListener != null ) {
			mListener.onClick( mBasePath + mFileList[ which ] );
		}
	}
	
	private FileDialogListener mListener;
	private AlertDialog mDialog;
	private String[] mFileList;
	private String   mBasePath;
}

public class MainActivity extends CardboardActivity implements StereoRenderer {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		CardboardView cardboardView = (CardboardView) findViewById( R.id.cardboard_view );
		cardboardView.setRenderer( this );
		setCardboardView(cardboardView);
		mThis = this;
		
		// このくらいのnear,farに設定してみる.
		cardboardView.setZPlanes( 1.0f,  1000.0f );
		
		FileDialog dlg = new FileDialog( this, "PMDモデルを選択して下さい", getResourcePath(), ".pmd", 
				new FileDialogListener() {
					@Override
					public void onClick(String selectedFile) {
						setPmdFile( selectedFile );
					}
				});
		dlg.show();
	}
	

	public void onNewFrame(HeadTransform headTransform ) {
		if( mMmdModel == null || mVmdResource == null ) {
			return;
		}
		if( !mMmdModel.isInitializeGL() ) {
			mMmdModel.createGLResource(); // 初回フレームでGLの初期化.
		}
		
		float camZ = 0.01f;
		Matrix.setLookAtM( mCamera, 0, 0.0f, 0.0f, camZ, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
		
		float[] mtx = new float[16];
		Matrix.setIdentityM( mtx, 0 );
		Matrix.translateM(mtx, 0, 0, -12, -32.5f );
		mMmdModel.setWorldMatrix(mtx);
		mMmdModel.update();
	
	}

	public void onDrawEye(EyeTransform eyeTransform ) {
		if( mMmdModel == null || mVmdResource == null ) {
			return;
		}
		GLES20.glClearColor( 0.01f, 0.01f, 0.01f, 0.0f );
    	GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    	float[] mtxProj = eyeTransform.getPerspective();
    	float[] mtxView = new float[16];
        Matrix.multiplyMM(mtxView, 0, eyeTransform.getEyeView(), 0, mCamera, 0);
        mMmdModel.draw( mtxView, mtxProj );

        if( mGridPlane != null ) {
        	mGridPlane.draw( mtxView, mtxProj );
        }
	}

	public void onFinishFrame(Viewport arg0) {
	}

	public void onRendererShutdown() {
	}

	public void onSurfaceChanged(int arg0, int arg1) {
	}

	public void onSurfaceCreated(EGLConfig arg0) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        mGridPlane = new GridPlane();
	}
	
	static MainActivity getInstance() {
		return mThis;
	}
	
	static String getResourcePath() {
        String sdcardPath = getExternalSDcardPath();
        StringBuilder filePath = new StringBuilder();
        
        if( sdcardPath != null ) {
        	filePath.append( sdcardPath );
        } else {
        	filePath.append( "./" );
        }
        filePath.append( RESOURCE_PATH );
        return filePath.toString();
	}
	public float[] getLightPos() { 
		return mLightPos;
	}
	/// リソースからシェーダーを作成.
	public int loadGLShader( int type, int resId ) {
		String code = readRawTextFile(resId);
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, code);
		GLES20.glCompileShader(shader);

    	final int[] compileStatus = new int[1];
    	GLES20.glGetShaderiv( shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0 );
    	if( compileStatus[0] == 0 ) {
    		String errmsg = GLES20.glGetShaderInfoLog(shader);
    		Log.e("DEBUG", "Error compiling shader: " + errmsg );
    		GLES20.glDeleteShader(shader);
    		shader = 0;
    	}
    	if( shader == 0 ) {
    		throw new RuntimeException("Error creating shader.");
    	}
    	return shader;
	}
	
	private void setPmdFile( String name ) {
		try {
			mMmdModel = new MMDModelResource( new FileInputStream( new File(name)));
			FileDialog dlg = new FileDialog( this, "VMDデータを選択して下さい", getResourcePath(), ".vmd", 
					new FileDialogListener() {
						@Override
						public void onClick(String selectedFile) {
							setVmdFile( selectedFile );
						}
					});
			dlg.show();
		} catch( FileNotFoundException e ) {
			e.printStackTrace();
		}
	}
	private void setVmdFile( String name ) {
		try {
			mVmdResource = new MMDAnimResource( new FileInputStream( new File(name)));
			if( mVmdResource != null ) {
				mMmdModel.attach(mVmdResource);
			}
		} catch( FileNotFoundException e ) {
			e.printStackTrace();
		}
	}
	
	private String readRawTextFile( int resId ) {
		InputStream inputStream = getResources().openRawResource(resId);
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			StringBuilder sb = new StringBuilder();
			String line;
			while( (line = reader.readLine()) != null ) {
				sb.append(line).append( "\n" );
			}
			reader.close();
			return sb.toString();
		} catch(IOException e) {
			e.printStackTrace();
		}
		return "";
	}
	
    static String getExternalSDcardPath() {
    	String path = null;
    	path = System.getenv( "EXTERNAL_STORAGE2" ); // GalaxyS2(2.3.4)
    	if( path != null ) {
    		return path;
    	}
    	// Android 4.4(S4,XPERIA,etc...)
    	path = System.getenv( "SECONDARY_STORAGE" );
    	if( path != null ) {
    		return path;
    	}
    	return path;
    }
	
	// データ参照パス(外部SDカード上のこのディレクトリにデータがあるものとする).
    private static final String RESOURCE_PATH = "/mikuvr/";
	
    private GridPlane mGridPlane;
    private MMDModelResource mMmdModel;
    private MMDAnimResource mVmdResource;
    private float[] mWorldMatrix = new float[16];
    private float[] mCamera = new float[16];
    private float[] mLightPos = new float[]{2.f, 5.0f, 3.0f, 0.0f};
    private static MainActivity mThis;
}

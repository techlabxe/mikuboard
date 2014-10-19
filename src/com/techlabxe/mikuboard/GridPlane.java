package com.techlabxe.mikuboard;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.opengl.GLES20;
import android.opengl.Matrix;

public class GridPlane {
    GridPlane() {
 	   float[] vert = new float[] { 
 			   -200.0f, 0.0f, -200.0f,
 			   -200.0f, 0.0f,  200.0f,
 			    200.0f, 0.0f, -200.0f,
 			    200.0f, 0.0f,  200.0f,
 	   };
 	   float[] normal = new float[]{ 0.0f, 1.0f, 0.0f };
 	   float[] color = new float[]{ 0.0f, 0.3398f, 0.9023f, 1.0f };
 	   final int VERTEX_FLOAT_COUNT = 3 + 3 + 4; // Position,Normal, Color
 	   final int VERTEX_COUNT = 4;
 	   FloatBuffer fb = ByteBuffer.allocateDirect(VERTEX_COUNT*VERTEX_FLOAT_COUNT*BYTES_PER_FLOAT)
 			   .order(ByteOrder.nativeOrder()).asFloatBuffer();
 	   for( int i = 0; i < VERTEX_COUNT; ++i ) {
 		   fb.put( vert, i*3, 3 ); // Position
 		   fb.put( normal, 0, 3 ); // Normal
 		   fb.put( color, 0, 4 ); // Color
 	   }
 	   fb.position(0);
 	   
 	   short[] indices = new short[] {
 			   0, 1, 2,  2, 1, 3,
 	   };
 	   ShortBuffer bb = ByteBuffer.allocateDirect(indices.length*BYTES_PER_SHORT).order(ByteOrder.nativeOrder()).asShortBuffer();
 	   bb.put( indices ).position(0);
 	   
 	   GLES20.glGenBuffers( 2, mVBO, 0 );
 	   GLES20.glBindBuffer( GLES20.GL_ARRAY_BUFFER, mVBO[0] );
 	   GLES20.glBufferData( GLES20.GL_ARRAY_BUFFER, fb.capacity()*BYTES_PER_FLOAT,  fb, GLES20.GL_STATIC_DRAW );
 	   GLES20.glBindBuffer( GLES20.GL_ELEMENT_ARRAY_BUFFER, mVBO[1] );
 	   GLES20.glBufferData( GLES20.GL_ELEMENT_ARRAY_BUFFER, bb.capacity()*BYTES_PER_SHORT, bb, GLES20.GL_STATIC_DRAW );
 	   
 	   Matrix.setIdentityM( mWorldMatrix, 0 );
 	   Matrix.translateM(mWorldMatrix, 0, 0, -12.0f, 0.0f);
 	   
 	   MainActivity act = MainActivity.getInstance();
	   
 	   int vertexShader = act.loadGLShader( GLES20.GL_VERTEX_SHADER, R.raw.vertex_grid );
 	   int fragmentShader=act.loadGLShader( GLES20.GL_FRAGMENT_SHADER, R.raw.fragment_grid );
 	   mPlaneShaderProgram = GLES20.glCreateProgram();
 	   GLES20.glAttachShader(mPlaneShaderProgram, vertexShader);
 	   GLES20.glAttachShader(mPlaneShaderProgram, fragmentShader);
 	   GLES20.glLinkProgram(mPlaneShaderProgram);    	   
 	   
 	   handleMVP = GLES20.glGetUniformLocation( mPlaneShaderProgram, "u_MVP" );
 	   handleWorld = GLES20.glGetUniformLocation(mPlaneShaderProgram, "u_World" );
 	   handleWorldView =  GLES20.glGetUniformLocation(mPlaneShaderProgram, "u_MVMatrix" );
 	   handleLightPosInEyeSpace = GLES20.glGetUniformLocation( mPlaneShaderProgram, "u_LightPos" );
    }

    
    public void draw(float[] mtxView, float[] mtxProj ) {
  	   int stride = BYTES_PER_FLOAT*(3+3+4);
  	   GLES20.glEnable( GLES20.GL_DEPTH_TEST );
  	   GLES20.glUseProgram(mPlaneShaderProgram);
  	   int handlePos = GLES20.glGetAttribLocation( mPlaneShaderProgram, "a_Position" );
  	   int handleNrm = GLES20.glGetAttribLocation( mPlaneShaderProgram, "a_Normal" );
  	   int handleCol = GLES20.glGetAttribLocation( mPlaneShaderProgram, "a_Color" );
  	   GLES20.glBindBuffer( GLES20.GL_ARRAY_BUFFER, mVBO[0] );
  	   GLES20.glVertexAttribPointer( handlePos, 3, GLES20.GL_FLOAT, false, stride, 0 );
  	   GLES20.glVertexAttribPointer( handleNrm, 3, GLES20.GL_FLOAT, false, stride, 12);
  	   GLES20.glVertexAttribPointer( handleCol, 4, GLES20.GL_FLOAT, false, stride, 24 );
  	   GLES20.glEnableVertexAttribArray(handlePos);
  	   GLES20.glEnableVertexAttribArray(handleNrm);
  	   GLES20.glEnableVertexAttribArray(handleCol);
  	   
  	   float[] mtxPVW = new float[16];
  	   float[] lightPosInEyeSpace = new float[4];
  	   Matrix.multiplyMM( mWorldView, 0, mtxView, 0, mWorldMatrix, 0 );
  	   Matrix.multiplyMM( mtxPVW, 0, mtxProj, 0, mWorldView, 0 );
  	   Matrix.multiplyMV(lightPosInEyeSpace, 0, mWorldView, 0, mLightPos, 0 );
  	   
  	   GLES20.glUniformMatrix4fv( handleMVP, 1, false, mtxPVW, 0 );
  	   GLES20.glUniformMatrix4fv( handleWorld, 1, false, mWorldMatrix, 0 );
  	   GLES20.glUniformMatrix4fv( handleWorldView, 1, false, mWorldView, 0 );
  	   GLES20.glUniform3fv(handleLightPosInEyeSpace, 1, lightPosInEyeSpace,0 );
  	   
  	   GLES20.glBindBuffer( GLES20.GL_ELEMENT_ARRAY_BUFFER,  mVBO[1] );
  	   GLES20.glDrawElements( GLES20.GL_TRIANGLES, 6,  GLES20.GL_UNSIGNED_SHORT, 0 );
  	   GLES20.glUseProgram(0);

  	   GLES20.glBindBuffer( GLES20.GL_ARRAY_BUFFER, 0 );
 	   GLES20.glUseProgram(0);
    }
    
	public static final int BYTES_PER_FLOAT = 4;
	public static final int BYTES_PER_SHORT = 2;
    private float[] mLightPos = new float[]{ 0.0f, 2.f, 0.0f, 1.0f };
    private float[] mWorldMatrix = new float[16];
    private float[] mWorldView = new float[16];
    private int[] mVBO = new int[]{-1,-1};
    private int mPlaneShaderProgram;
    private int handleMVP, handleWorld, handleWorldView, handleLightPosInEyeSpace;

}

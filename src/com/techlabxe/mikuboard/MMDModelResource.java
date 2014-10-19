package com.techlabxe.mikuboard;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;

import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

public class MMDModelResource {
	public MMDModelResource( InputStream in ) {
		mIsInitializeGL = false;
		readPMD( in );
	}
	public boolean isInitializeGL() {
		return mIsInitializeGL;
	}
	public void attach( MMDAnimResource anim ) {
		mAnimResource = anim;
	}
	public void setWorldMatrix( float[] world ) {
		System.arraycopy( world, 0, mWorld, 0, 16 );
	}
	public void update() {
		if( mAnimResource == null ) {
			for( MmdBone bone : mBoneList ) {
				bone.updateWorldMatrix(mWorld);
			}
			return;
		}
		if( mStartTime == 0 ) {
			mStartTime = SystemClock.uptimeMillis();
		}
		int nextAnimFrame = (int)((SystemClock.uptimeMillis() - mStartTime) / 32.0 );
		if( mAnimationFrame + 5 < nextAnimFrame ) {
			mAnimationFrame += 5;
		} else {
			mAnimationFrame = nextAnimFrame;
		}
		if( mAnimResource.getAnimationPeriod() <= mAnimationFrame ) {
			mAnimationFrame = 0;
			mStartTime = SystemClock.uptimeMillis();
		}
		
		float[] translation = new float[3];
		float[] rotation = new float[4];
		for( MmdBone bone : mBoneList ) {
			String boneName= bone.mName;
			AnimNode animNode = mAnimResource.getAnimNode(boneName);
			if( animNode != null ) {
				animNode.calcAnimation( mAnimationFrame, translation, rotation );
				for( int i = 0; i < 3; ++i ) {
					bone.mTranslation[i] = bone.mInitTranslation[i] + translation[i];
				}
				for( int i = 0; i < 4; ++i ) {
					bone.mRotation[i] = rotation[i];
				}
				bone.updateLocalMatrix();
			}
		}
		for( int i  = 0; i < mBoneList.length; ++i ) {
			MmdBone bone = mBoneList[i];
			bone.updateWorldMatrix( mWorld );
		}
		// IK process
		for( MmdIkBone ikBone : mIkBoneList ) {
			ikBone.update();
		}
	}
	public void createGLResource() {
		mIsInitializeGL = true;
		MainActivity act = MainActivity.getInstance();
		FloatBuffer fb = ByteBuffer.allocateDirect(mStride*mPosition.length).order(ByteOrder.nativeOrder()).asFloatBuffer();
		int num = mPosition.length;
		float[] floatBuf = new float[ num * (3+3+2) ];
		for( int i = 0; i < num; ++i ) {
			floatBuf[i*8+0] = mPosition[i].x;
			floatBuf[i*8+1] = mPosition[i].y;
			floatBuf[i*8+2] = mPosition[i].z;
			floatBuf[i*8+3] = mNormal[i].x;
			floatBuf[i*8+4] = mNormal[i].y;
			floatBuf[i*8+5] = mNormal[i].z;
			floatBuf[i*8+6] = mTexUV[i].x;
			floatBuf[i*8+7] = mTexUV[i].y;
		}
		fb.put( floatBuf );
		fb.position(0);
		
		GLES20.glGenBuffers( 2, mVBO, 0 );
		GLES20.glBindBuffer( GLES20.GL_ARRAY_BUFFER, mVBO[0] );
		GLES20.glBindBuffer( GLES20.GL_ELEMENT_ARRAY_BUFFER, mVBO[1] );
		GLES20.glBufferData( GLES20.GL_ARRAY_BUFFER, fb.capacity()*BYTES_PER_FLOAT, fb, GLES20.GL_STATIC_DRAW );
		GLES20.glBufferData( GLES20.GL_ELEMENT_ARRAY_BUFFER, mIndices.capacity()*BYTES_PER_INT, mIndices, GLES20.GL_STATIC_DRAW );

		Matrix.setIdentityM( mWorld,  0);
		int vertexShader = act.loadGLShader( GLES20.GL_VERTEX_SHADER, R.raw.vertex_chara );
		int fragmentShader=act.loadGLShader( GLES20.GL_FRAGMENT_SHADER, R.raw.fragment_chara );
		mShaderProgram = GLES20.glCreateProgram();
		GLES20.glAttachShader( mShaderProgram, vertexShader );
		GLES20.glAttachShader( mShaderProgram, fragmentShader);
		GLES20.glLinkProgram(mShaderProgram);

		handleVP = GLES20.glGetUniformLocation(mShaderProgram, "u_mtxVP");
		handleWorld = GLES20.glGetUniformLocation(mShaderProgram, "u_World");
		handleWorldView = GLES20.glGetUniformLocation(mShaderProgram,"u_MVMatrix");
		handleLightPosInEyeSpace = GLES20.glGetUniformLocation(mShaderProgram,"u_LightPos");
		handleDiffuse = GLES20.glGetUniformLocation(mShaderProgram, "u_diffuseColor" );
		handleTexture = GLES20.glGetUniformLocation(mShaderProgram, "texture" );
		handleUseTexture = GLES20.glGetUniformLocation(mShaderProgram, "u_useTexture" );
		handleBoneMatrices = GLES20.glGetUniformLocation(mShaderProgram, "u_BoneMatrices" );
		
		String filePathBase = MainActivity.getResourcePath();
		int materialCount = mMaterials.length;
		for( int i = 0; i < materialCount; ++i ) {
			for( SkinSegment s : mMaterials[i].mSkinSegments ) {
				s.createIndexBuffer();
				s.createWeightIndicesBuffer(mSkinInfo, num );
			}
			try {
				if( mMaterials[i].textureFileName.length() < 1 ) {
					continue;
				}
				String path =filePathBase + mMaterials[i].textureFileName;
				InputStream in = new FileInputStream( path );
				Bitmap bitmap = BitmapFactory.decodeStream(in);
				
				int[] texObj = new int[1];
				GLES20.glGenTextures(1, texObj, 0);
				GLES20.glBindTexture( GLES20.GL_TEXTURE_2D, texObj[0] );
				mMaterials[i].mTextureGL = texObj[0];
				GLUtils.texImage2D( GL10.GL_TEXTURE_2D, 0, bitmap, 0 );
				GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR );
				GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR );
				GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT );
				GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT );
				bitmap.recycle();
				in.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}		
	}
	public void draw( float[] mtxView, float[] mtxProj ) {
		MainActivity act = MainActivity.getInstance();
		GLES20.glUseProgram( mShaderProgram );

		int handlePos = GLES20
				.glGetAttribLocation(mShaderProgram, "a_Position");
		int handleNrm = GLES20.glGetAttribLocation(mShaderProgram, "a_Normal");
		int handleTex = GLES20.glGetAttribLocation(mShaderProgram, "a_UV");
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVBO[0]);
		GLES20.glVertexAttribPointer(handlePos, 3, GLES20.GL_FLOAT, false,
				mStride, 0);
		GLES20.glVertexAttribPointer(handleNrm, 3, GLES20.GL_FLOAT, false,
				mStride, 12);
		GLES20.glVertexAttribPointer(handleTex, 2, GLES20.GL_FLOAT, false,
				mStride, 24);
		GLES20.glEnableVertexAttribArray(handlePos);
		GLES20.glEnableVertexAttribArray(handleNrm);
		GLES20.glEnableVertexAttribArray(handleTex);

		float[] mtxPV = new float[16];
		float[] mtxWorldView = new float[16];
		float[] lightPosInEyeSpace = new float[4];
		float[] lightPos = act.getLightPos();
		
		Matrix.multiplyMM( mtxWorldView, 0, mtxView, 0, mWorld, 0 );
		Matrix.multiplyMM( mtxPV, 0, mtxProj, 0, mtxView, 0 );
		Matrix.multiplyMV( lightPosInEyeSpace, 0, mWorld, 0, lightPos, 0 );
		
		GLES20.glUniformMatrix4fv( handleVP, 1, false, mtxPV, 0 );
		GLES20.glUniformMatrix4fv( handleWorld, 1, false, mWorld, 0 );
		GLES20.glUniformMatrix4fv( handleWorldView, 1, false, mtxWorldView, 0 );
		GLES20.glUniform3fv( handleLightPosInEyeSpace, 1, lightPosInEyeSpace, 0 );
		
		for( MmdMaterial material : mMaterials ) {
			GLES20.glBindTexture( GLES20.GL_TEXTURE_2D, material.mTextureGL );
			GLES20.glUniform1i( handleTexture, 0 );
			if( material.mTextureGL != 0 ) {
				GLES20.glUniform4f( handleUseTexture, 1.0f, 0.0f, 0.0f, 0.0f );
			} else {
				GLES20.glUniform4f( handleUseTexture, 0.0f, 0.0f, 0.0f, 0.0f );
			}
			GLES20.glUniform4f( handleDiffuse, material.diffuseColor.x, material.diffuseColor.y, material.diffuseColor.z, material.alpha );
			
			int handleVertexBlend = GLES20.glGetAttribLocation(mShaderProgram, "a_Blend" );
			for( SkinSegment s : material.mSkinSegments ) {
				int[] paletteTable = s.getBonePalette();
				float[] mtxPalette = new float[ 16 * paletteTable.length ];
				
				for( int i = 0; i < paletteTable.length; ++i ) {
					int boneIndex = paletteTable[i];
					float[] mtxWorld = mBoneList[ boneIndex ].mtxWorld;
					float[] mtxInvBind = mBoneList[ boneIndex ].mtxInvBind;
					Matrix.multiplyMM(mtxPalette, 16*i, mtxWorld, 0, mtxInvBind, 0 );
				}
				GLES20.glUniformMatrix4fv( handleBoneMatrices, paletteTable.length, false, mtxPalette, 0 );
				
				int vertexBlendVBO = s.getBlendBuffer();
				GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBlendVBO );
				GLES20.glVertexAttribPointer( handleVertexBlend, 4, GLES20.GL_UNSIGNED_BYTE, true, 4, 0 );
				GLES20.glEnableVertexAttribArray(handleVertexBlend);
				
				int indexCount = s.getIndexCount();
				GLES20.glBindBuffer( GLES20.GL_ELEMENT_ARRAY_BUFFER, s.getIndexBuffer() );
				GLES20.glDrawElements( GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_INT, 0 );
			}
			GLES20.glDisableVertexAttribArray(handleVertexBlend);
		}
		
		GLES20.glDisableVertexAttribArray(handleTex);
		GLES20.glBindBuffer( GLES20.GL_ELEMENT_ARRAY_BUFFER, 0 );
	}
	
	private void readPMD( InputStream in ) {
		try {
			MMDDataReader reader = new MMDDataReader(in);
			reader.readByte(); reader.readByte(); reader.readByte();
			float version = reader.readFloat();
			String name = reader.readAscii(20);
			String comment = reader.readAscii(256);
			
			int numVertices = reader.readInt();
			mPosition = MmdVec3.createArray( numVertices );
			mNormal = MmdVec3.createArray(numVertices);
			mTexUV = MmdVec2.createArray(numVertices);
			MmdSkinInfo[] skininfo = new MmdSkinInfo[numVertices];
			mSkinInfo = skininfo;
			
			for( int i = 0; i < numVertices; ++i ) {
				mPosition[i] = reader.readVec3();
				mNormal[i] = reader.readVec3();
				mTexUV[i] = reader.readVec2();
				skininfo[i] = readSkinInfo( reader );
				reader.readByte(); // EdgeFlag;
				
				mPosition[i].z *= -1;
				mNormal[i].z *= -1;
			}
			int numIndices = reader.readInt();
			int[] indices = new int[ numIndices ];
			int numFaces = numIndices / 3;
			for( int i = 0; i < numFaces; ++i ) {
				indices[ i*3 + 0 ] = reader.readUShort();
				indices[ i*3 + 1 ] = reader.readUShort();
				indices[ i*3 + 2 ] = reader.readUShort();
			}
			mIndices = ByteBuffer.allocateDirect( BYTES_PER_INT * numIndices ).order(ByteOrder.nativeOrder()).asIntBuffer();
			mIndices.put(indices).position(0);

			int numMaterials = reader.readInt();
			mMaterials = new MmdMaterial[numMaterials];
			int startIndex = 0;
			for( int i = 0; i < numMaterials; ++i ) {
				mMaterials[i] = readMaterial( reader );
				//ArrayList<Integer> boneArrays = new ArrayList<Integer>();
				HashSet<Integer> boneArrays = new HashSet<Integer>(); // ArrayListでやるよりHashSetのほうが早かった.
				int numFacesIndicesAtMaterial = mMaterials[i].faceVertexCount;

				{	// ボーンのための描画単位分割処理.
					ArrayList<SkinSegment> segments = new ArrayList<SkinSegment>();
					int faceCount = numFacesIndicesAtMaterial / 3;
					int[] faceIndices = new int[3];
					for( int j = 0; j < faceCount; ++j ) {
						int indexOffset = startIndex + 3*j;
						for( int k = 0; k < 3; ++k ) { faceIndices[k] = indices[ indexOffset+k ]; }
						boolean isAddable = false;
						SkinSegment segment = null;
						for( SkinSegment s : segments ) {
							isAddable = s.isAddable( faceIndices, skininfo );
							if( isAddable ) {
								segment = s;
								break;
							}
						}
						if( segment == null ) {
							segment = new SkinSegment();
							segments.add( segment );
							segment.isAddable(faceIndices, skininfo); // ここでは登録出来るはず.
						}
						segment.addFaceIndices( faceIndices );
					}
					mMaterials[i].mSkinSegments = segments;
				}
				startIndex += mMaterials[i].faceVertexCount;
				
			}
			int numBone = reader.readShort();
			mBoneList = new MmdBone[numBone];
			for( int i = 0; i < numBone; ++i ) {
				MmdBone bone = readBone(reader);
				mBoneList[i] = bone;
			}
			setupBone();
			if( mBoneList != null ) {
				float[] mtxWorld = new float[16];
				Matrix.setIdentityM(mtxWorld, 0);
				for( MmdBone b : mBoneList ) {
					b.updateWorldMatrix(null);
				}
			}
			
 			for( MmdMaterial m : mMaterials ) {
				for( SkinSegment s : m.mSkinSegments ) {
					s.createPalette(); // パレット作成.
					int[] boneTable = new int[numBone];
					int[] useBoneList = s.getBonePalette();
					for( int i = 0; i < numBone; ++i ) { boneTable[i] = -1; }
					s.setBoneMappingTable(boneTable);
					for( int i = 0; i < useBoneList.length; ++i ) {
						boneTable[ useBoneList[i] ] = i;
					}
				}
			}
 			
 			int numIKbone = reader.readShort();
 			mIkBoneList = new MmdIkBone[ numIKbone ];
 			for( int i = 0; i < numIKbone; ++i ) {
 				int ikTargetIndex = reader.readShort();
 				int ikEffIndex = reader.readShort();
 				int lengthOfChains = reader.readByte();
 				int iterations = reader.readShort();
 				float weight = reader.readFloat();
 				
 				MmdBone ikTargetBone = mBoneList[ ikTargetIndex ];
 				MmdBone ikEffBone = mBoneList[ ikEffIndex ];
 				
 				MmdBone[] ikChainList = new MmdBone[ lengthOfChains ];
 				for( int j = 0; j < lengthOfChains; ++j ) {
 					int boneIndex = reader.readShort();
 					ikChainList[j] = mBoneList[ boneIndex ];
 				}
 				mIkBoneList[i] = new MmdIkBone( ikTargetBone, ikEffBone, ikChainList );
 				mIkBoneList[i].setIteration( (short)iterations);
 				mIkBoneList[i].setControlWeight(weight);
 			}
			
		} catch( IOException e ) {
			
		}
	}
	
	private MmdSkinInfo readSkinInfo( MMDDataReader reader ) {
		MmdSkinInfo info = new MmdSkinInfo();
		info.boneA = reader.readShort();
		info.boneB = reader.readShort();
		info.weight = (float) reader.readByte() / 100.0f;
		return info;
	}
	private MmdMaterial readMaterial( MMDDataReader reader ) {
		MmdMaterial material = new MmdMaterial();
		material.diffuseColor = reader.readVec3();
		material.alpha =reader.readFloat();
		material.specular = reader.readFloat();
		material.specularColor = reader.readVec3();
		material.ambientColor = reader.readVec3();
		material.toonIndex = reader.readByte();
		material.edgeFlag = reader.readByte();
		material.faceVertexCount = reader.readInt();
		material.textureFileName = reader.readAscii( 20 );
		return material;
	}
	private MmdBone readBone( MMDDataReader reader ) {
		MmdBone bone = new MmdBone();
		bone.mName = reader.readAscii(20);
		bone.mParentIndex = reader.readShort();
		int childIndex = reader.readShort();
		if( childIndex == 0 ) { childIndex = -1; } // いつの頃からか末端ボーンは子インデックスとしてゼロになったようで.
		bone.mChildIndex = childIndex;
		bone.mType = reader.readByte();
		bone.mIKParentBoneIndex = reader.readShort();
		bone.mBonePosition = reader.readVec3();
		bone.mBonePosition.z *= -1.0f;
		bone.mRotation[0] = 0.0f; bone.mRotation[1] = 0.0f; bone.mRotation[2] = 0.0f; bone.mRotation[3] = 1.0f;
		return bone;
	}
	private void setupBone() {
		int numBone = mBoneList.length;
		for(int i = 0; i < numBone; ++i ) {
			MmdBone bone = mBoneList[i];
			if( bone.mParentIndex != -1 ) {
				bone.mParentNode = mBoneList[ bone.mParentIndex ];
			}
			if( bone.mChildIndex != -1 ) {
				bone.mChildNode = mBoneList[ bone.mChildIndex ];
			}
		}
		
		for( int i = 0; i < numBone; ++i ) {
			MmdBone bone = mBoneList[i];
			if( bone.mParentIndex != -1 ) {
				MmdBone parent = mBoneList[ bone.mParentIndex ];
				bone.mParentNode = parent;
				bone.mTranslation[0] = bone.mBonePosition.x - parent.mBonePosition.x;
				bone.mTranslation[1] = bone.mBonePosition.y - parent.mBonePosition.y;
				bone.mTranslation[2] = bone.mBonePosition.z - parent.mBonePosition.z;
			} else {
				bone.mTranslation[0] = bone.mBonePosition.x;
				bone.mTranslation[1] = bone.mBonePosition.y;
				bone.mTranslation[2] = bone.mBonePosition.z;
			}
			System.arraycopy(bone.mTranslation, 0, bone.mInitTranslation, 0, bone.mTranslation.length );
			Matrix.setIdentityM( bone.mtxInvBind, 0 );
			Matrix.translateM( bone.mtxInvBind, 0, -bone.mBonePosition.x, -bone.mBonePosition.y, -bone.mBonePosition.z );
			Matrix.setIdentityM( bone.mtxLocal, 0 );
			Matrix.translateM( bone.mtxLocal, 0, bone.mTranslation[0], bone.mTranslation[1], bone.mTranslation[2]);
		}
	}	
	
	public static final int BYTES_PER_FLOAT = 4;
	public static final int BYTES_PER_INT = 4;
	
	private MmdVec3[] mPosition, mNormal;
	private MmdVec2[] mTexUV;
	
	private IntBuffer mIndices;
	private MmdMaterial[] mMaterials;
	private MmdBone[] mBoneList;
	private MmdSkinInfo[] mSkinInfo;
	private MmdIkBone[] mIkBoneList;
	
	private int mAnimationFrame;
	private MMDAnimResource mAnimResource;
	private long mStartTime;
	
	private float[] mWorld = new float[16];
	private boolean mIsInitializeGL;
	private int mStride = (BYTES_PER_FLOAT * (3+3+2) ); // PNT
	private int[] mVBO = new int[2];
	private int mShaderProgram;
	private int handleVP, handleWorld, handleWorldView, handleLightPosInEyeSpace;
	private int handleTexture, handleDiffuse, handleUseTexture;
	private int handleBoneMatrices;
}

class MmdSkinInfo {
	public float weight;
	public int boneA, boneB;
}
class MmdMaterial {
	public MmdVec3 diffuseColor;
	public float   alpha;
	public MmdVec3 specularColor;
	public float   specular;
	public MmdVec3 ambientColor;
	public int toonIndex;
	public int edgeFlag;
	public int faceVertexCount;
	public String textureFileName;

	public ArrayList<SkinSegment> mSkinSegments;
	
	public int mTextureGL;
}


class MmdBone {
	public void updateLocalMatrix() {
		float[] mtxTranslation = new float[16];
		Matrix.setIdentityM(mtxTranslation, 0);
		Matrix.translateM(mtxTranslation, 0, mTranslation[0], mTranslation[1], mTranslation[2] );
		
		// Normalize Quaternion
		float length = 0.0f;
		for( int i = 0; i < 4; ++i ) {
			length += mRotation[i] * mRotation[i];
		}
		length = (float)Math.sqrt(length);
		if( length > 0.01f ) {
			length = 1.0f / length;
			for( int i = 0; i < 4; ++i ) {
				mRotation[i] *= length;
			}
		}
				
		
		// Quaternion to Matrix
		float xx = mRotation[0] * mRotation[0];
		float yy = mRotation[1] * mRotation[1];
		float zz = mRotation[2] * mRotation[2];
		float xy = mRotation[0] * mRotation[1];
		float xz = mRotation[0] * mRotation[2];
		float xw = mRotation[0] * mRotation[3];
		float yz = mRotation[1] * mRotation[2];
		float yw = mRotation[1] * mRotation[3];
		float zw = mRotation[2] * mRotation[3];

		// --- どうやらJava Matrixの配列の中身としては DirectX並びと同じ. そして演算順序だけがOpenGL風というもののようだ.
		mtxLocalRotation[0] = 1.0f - 2.0f * (yy + zz);
		mtxLocalRotation[4] = 2.0f * (xy - zw);
		mtxLocalRotation[8] = 2.0f * (xz + yw);
		mtxLocalRotation[12] = 0.0f;
		
		mtxLocalRotation[1] = 2.0f * (xy + zw);
		mtxLocalRotation[5] = 1.0f - 2.0f * (xx + zz);
		mtxLocalRotation[9] = 2.0f * (yz - xw);
		mtxLocalRotation[13] = 0.0f;
		
		mtxLocalRotation[2] = 2.0f * (xz - yw);
		mtxLocalRotation[6] = 2.0f * (yz + xw);
		mtxLocalRotation[10]= 1.0f - 2.0f * (xx+yy);
		mtxLocalRotation[14] = 0.0f;
		
		
		mtxLocalRotation[12] = 0.0f;
		mtxLocalRotation[13] = 0.0f;
		mtxLocalRotation[14] = 0.0f;
		mtxLocalRotation[15] = 1.0f;

		Matrix.setIdentityM(mtxLocal, 0);
		Matrix.multiplyMM( mtxLocal, 0, mtxTranslation, 0, mtxLocalRotation, 0 );		
	}
	public void updateWorldMatrix( float[] mtx ) {
		if( mParentIndex != -1 && mParentNode != null ) {
			Matrix.multiplyMM( mtxWorld, 0, mParentNode.mtxWorld, 0, mtxLocal, 0 );
		} else {
			if( mtx == null ) {
				System.arraycopy( mtxLocal, 0, mtxWorld, 0, mtxLocal.length );
			} else {
				Matrix.multiplyMM( mtxWorld, 0, mtx, 0, mtxLocal, 0 );
			}
		}
	}
	
	public float[] getWorldTranslation() {
		return new float[]{ mtxWorld[12], mtxWorld[13], mtxWorld[14] };
	}
	
	public MmdBone getParent() { return mParentNode; }
	public MmdBone getChild()  { return mChildNode; }
	
	public String mName;
	public float[] mtxInvBind = new float[16];
	public float[] mtxLocal = new float[16];
	public float[] mtxWorld = new float[16];
	public float[] mTranslation = new float[3];
	public float[] mRotation = new float[4];
	public float[] mInitTranslation = new float[3]; // 親からの相対にした値.初期値.アニメ計算分で必要になる.
	public float[] mtxLocalRotation = new float[16];
	int mType;
	int mParentIndex;
	int mChildIndex;
	
	int mIKParentBoneIndex; // IKボーン番号.(影響IKボーン.ない場合には0)
	public MmdVec3 mBonePosition;
	
	public MmdBone mParentNode;
	public MmdBone mChildNode;
}

class MmdIkBone {
	public MmdIkBone( MmdBone ikTarget, MmdBone ikEff, MmdBone[] ikChains ) {
		mTargetIK = ikTarget; mEff= ikEff;
		mIkChainsList = ikChains;
	}
	public void setIteration( short itrCount ) { mIterations = itrCount; }
	public void setControlWeight( float weight ) { mWeight = weight; }
	
	public int getIKChainNumber() { return mIkChainsList.length; }
	public float getControlWeight() { return mWeight; }
	public short getIteration() { return mIterations; }
	
	public void update() {
		for( int i = mIkChainsList.length-1; i >= 0; --i ) {
			mIkChainsList[i].updateLocalMatrix();
			mIkChainsList[i].updateWorldMatrix(null);
		}
		float[] targetPositionWorld = new float[4];
		System.arraycopy( mTargetIK.getWorldTranslation(), 0, targetPositionWorld, 0, 3 ); targetPositionWorld[3] = 0.0f;
		
		mEff.updateLocalMatrix();
		mEff.updateWorldMatrix(null);
		for( int itr = 0; itr < mIterations; ++itr ) {
			float[] mtxBone = new float[16];
			float[] mtxInvBone = new float[16];
			for( int i = 0; i < mIkChainsList.length; ++i ) {
				MmdBone bone = mIkChainsList[i];
				System.arraycopy( bone.mtxWorld, 0, mtxBone, 0, bone.mtxWorld.length );
				Matrix.invertM(mtxInvBone, 0, mtxBone, 0 );
				
				// エフェクタおよびターゲットのワールド位置を、現在のボーンのローカル空間での位置に変換.
				float[] effWorldTrans = new float[4];
				float[] effPositionL = new float[4];
				float[] targetPositionL = new float[4];
				System.arraycopy( mEff.getWorldTranslation(), 0, effWorldTrans, 0,  3 );
				effWorldTrans[3] = 1.0f;
				targetPositionWorld[3] = 1.0f;
				Matrix.multiplyMV( effPositionL, 0, mtxInvBone, 0, effWorldTrans, 0 );
				Matrix.multiplyMV( targetPositionL, 0, mtxInvBone, 0, targetPositionWorld, 0 );
				
				float dx = effPositionL[0] - targetPositionL[0];
				float dy = effPositionL[1] - targetPositionL[1];
				float dz = effPositionL[2] - targetPositionL[2];
				float distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
				if( distance < 0.0001f ) {
					return; // 十分に近いので終了.
				}
				
				// 基準関節からエフェクタへ向かうベクトルと、基準関節からターゲット位置へ向かうベクトルを求める.
				float[] effVector = new float[3];
				float[] targetVector = new float[3];
				System.arraycopy( effPositionL, 0, effVector, 0, 3 ); // 実は既にローカル座標系での位置のため、正規化すればベクトルになる.
				System.arraycopy( targetPositionL, 0, targetVector, 0, 3 );
				normalizeVector3( effVector );
				normalizeVector3( targetVector );
				
				float dotProd = dotProduct( effVector, targetVector );
				float rotRadian = (float)Math.acos( dotProd );
				if( 0.000001f < Math.abs(rotRadian) ) {
					// 回転角の制限を適用.
					if( rotRadian < -mWeight ) { rotRadian = -mWeight; }
					if( rotRadian > mWeight ) { rotRadian = mWeight; }
					
					// 回転軸を求める.
					float[] vRotAxis = crossProduct( effVector, targetVector );
					if( dotProduct( vRotAxis, vRotAxis) < 0.00001f ) {
						// 回転軸が求まらないので放置.
						continue;
					}
					normalizeVector3( vRotAxis );
					
					float[] rotationQuaternion = new float[4];
					createQuaternionFromAxis(rotationQuaternion, vRotAxis, rotRadian );
					
					final String LegR = new String("右ひざ"), LegL = new String("左ひざ"); 
					if( bone.mName.equals(LegR) || bone.mName.equals(LegL) ) {
						// 膝の回転角度制限.
						float[] eulerXYZ = getQuaternionToEulerXYZ( rotationQuaternion );
						// 膝の角度制限を行う.
						if( eulerXYZ[0] > Math.PI ) { eulerXYZ[0] = (float)Math.PI; }
						if( 0.002f > eulerXYZ[0] ) { eulerXYZ[0] =  0.002f; }
						eulerXYZ[1] = 0.0f; eulerXYZ[2] = 0.0f;
						
						// この角度から再度クォータニオンを作成.
						{
							float xRadian = eulerXYZ[0] * 0.5f;
							float yRadian = eulerXYZ[1] * 0.5f;
							float zRadian = eulerXYZ[2] * 0.5f;
							float sinX = (float) Math.sin( xRadian ), cosX = (float) Math.cos( xRadian );
							float sinY = (float) Math.sin( yRadian ), cosY = (float) Math.cos( yRadian );
							float sinZ = (float) Math.sin( zRadian ), cosZ = (float) Math.cos( zRadian );
							rotationQuaternion[0] = sinX*cosY*cosZ - cosX*sinY*sinZ;
							rotationQuaternion[1] = cosX*sinY*cosZ + sinX*cosY*sinZ;
							rotationQuaternion[2] = cosX*cosY*sinZ - sinX*sinY*cosZ;
							rotationQuaternion[3] = cosX*cosY*cosZ + sinX*sinY*sinZ;
						}
					}
					normalizeQuaternion(rotationQuaternion);
					
					float[] v = new float[4];
					System.arraycopy( bone.mRotation, 0, v, 0, v.length );
					multiplyQuaternion( v, rotationQuaternion, v );
					normalizeQuaternion(v);
					System.arraycopy(v, 0, bone.mRotation, 0, v.length);
					
					for( int j = i; j >= 0; --j ) {
						mIkChainsList[j].updateLocalMatrix();
						mIkChainsList[j].updateWorldMatrix(null);
					}
					
					mEff.updateLocalMatrix();
					mEff.updateWorldMatrix(null);
				}
			}
		}
	}
	
	private MmdBone mTargetIK; // IKターゲットボーン.
	private MmdBone mEff;	// IK先端ボーン(エフェクタ)
	private MmdBone[] mIkChainsList;
	private short mIterations;
	private float mWeight;
	
	private float dotProduct( float[] v1, float[] v2 ) {
		return v1[0]*v2[0] + v1[1]*v2[1] + v1[2]*v2[2];
	}
	private float[] crossProduct( float[] v1, float[] v2 ) {
		float yz = v1[1]*v2[2], zy = v1[2]*v2[1];
		float zx = v1[2]*v2[0], xz = v1[0]*v2[2];
		float xy = v1[0]*v2[1], yx = v1[1]*v2[0];
		// 外積計算.
		float[] ret = new float[3];
		ret[0] = yz - zy;
		ret[1] = zx - xz;
		ret[2] = xy - yx;
		return ret;
	}
	private static void normalizeVector3( float[] v ) {
		double length = 0;
		for( int i=0;i<3;++i) { length += v[i]*v[i]; }
		length = 1.0 / Math.sqrt( length );
		for( int i=0;i<3;++i) { v[i] *= length; }
	}
	private void createQuaternionFromAxis( float[] outQuat, float[] axis, float angle ) {
		float halfAngle = angle * 0.5f;
		float sin = (float)Math.sin(halfAngle);
		outQuat[3] = (float)Math.cos( halfAngle );
		outQuat[0] = sin * axis[0];
		outQuat[1] = sin * axis[1];
		outQuat[2] = sin * axis[2];
	}
	private void normalizeQuaternion( float[] q ) {
		float len = 0.0f;
		for( float v : q ) {
			len += v*v;
		}
		len = (float)( 1.0 / Math.sqrt(len) );
		for( int i = 0; i < q.length; ++i ) {
			q[i] *= len;
		}
	}
	private void multiplyQuaternion( float[] outQuat, float[] q1, float[] q2 ) {
		float xx = q1[0] * q2[0], xy = q1[0] * q2[1], xz = q1[0] * q2[2], xw = q1[0] * q2[3];
		float yx = q1[1] * q2[0], yy = q1[1] * q2[1], yz = q1[1] * q2[2], yw = q1[1] * q2[3];
		float zx = q1[2] * q2[0], zy = q1[2] * q2[1], zz = q1[2] * q2[2], zw = q1[2] * q2[3];
		float wx = q1[3] * q2[0], wy = q1[3] * q2[1], wz = q1[3] * q2[2], ww = q1[3] * q2[3];
		outQuat[0] = xw + wx + zy - yz;
		outQuat[1] = yw - zx + wy + xz;
		outQuat[2] = zw + yx - xy + wz;
		outQuat[3] = ww - xx - yy - zz;
	}
	private float[] getQuaternionToEulerXYZ( float[] rotationQuaternion ) {
		float[] eulerXYZ = new float[3];
		// XYZ回転角を求める.
		float x2 = rotationQuaternion[0] + rotationQuaternion[0];
		float y2 = rotationQuaternion[1] + rotationQuaternion[1];
		float z2 = rotationQuaternion[2] + rotationQuaternion[2];
		float xz2 = rotationQuaternion[0] * z2;
		float wy2 = rotationQuaternion[3] * y2;
		float tmp = -(xz2 - wy2);
		if( tmp >= 1.0f ) { tmp = 1.0f; } else if( tmp <= -1.0f ) { tmp = -1.0f; }
		float yRadian = (float)Math.asin(tmp);
		
		float xx2 = rotationQuaternion[0] * x2;
		float xy2 = rotationQuaternion[0] * y2;
		float zz2 = rotationQuaternion[2] * z2;
		float wz2 = rotationQuaternion[3] * z2;
		if( yRadian < Math.PI * 0.5f ) {
			if( yRadian > -Math.PI * 0.5 ) {
				float yz2 = rotationQuaternion[1] * z2;
				float wx2 = rotationQuaternion[3] * x2;
				float yy2 = rotationQuaternion[1] * y2;
				eulerXYZ[0] = (float)Math.atan2( (yz2+wx2), (1.0f-(xx2+yy2)));
				eulerXYZ[1] = yRadian;
				eulerXYZ[2] = (float)Math.atan2( (xy2+wz2), (1.0f-(yy2+zz2)));
			} else {
				eulerXYZ[0] = -(float)Math.atan2( (xy2-wz2), (1.0f-(xx2+zz2)) );
				eulerXYZ[1] = yRadian;
				eulerXYZ[2] = 0.0f;
			}
		} else {
			eulerXYZ[0] = (float)Math.atan2( (xy2-wz2), (1.0f-(xx2+zz2)) );
			eulerXYZ[1] = yRadian;
			eulerXYZ[2] = 0.0f;
		}
		return eulerXYZ;
	}
}

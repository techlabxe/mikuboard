package com.techlabxe.mikuboard;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import android.opengl.GLES20;

public class SkinSegment {
	public final static int BONE_NUMBER = 32;
	public SkinSegment() {
		mBoneList = new int[BONE_NUMBER];
		for( int i = 0; i < BONE_NUMBER; ++i ) {
			mBoneList[i] = -1;
		}
	}
	public boolean isAddable( int[] indices, MmdSkinInfo[] skininfo ) {
		boolean ret = false;
		ArrayList<Integer> needAddBoneId = new ArrayList<Integer>();
		for( int i = 0; i < 3; ++i ) {
			int boneId = skininfo[ indices[i]].boneA;
			int indexPos = Arrays.binarySearch( mBoneList, boneId );
			if( indexPos < 0 ) {
				needAddBoneId.add( Integer.valueOf(boneId));
			}
			boneId = skininfo[indices[i]].boneB;
			indexPos = Arrays.binarySearch( mBoneList, boneId );
			if( indexPos < 0 ) {
				needAddBoneId.add( Integer.valueOf(boneId));
			}
		}
		if( needAddBoneId.isEmpty() ) {
			ret = true;
		} else {
			final int needAddCount = needAddBoneId.size();
			boolean allAdded = true;
			for( int i = 0; i < needAddCount; ++i ) {
				int indexPos = -1;
				int boneId = needAddBoneId.get(i).intValue();
				indexPos = Arrays.binarySearch( mBoneList, boneId );
				if( indexPos >= 0 ) {
					continue; // 既に登録済み.
				}
				indexPos = Arrays.binarySearch(mBoneList, -1);
				if( indexPos >= 0 ) {
					mBoneList[ indexPos ] = boneId;
					Arrays.sort( mBoneList );
				} else {
					allAdded = false;
					break;
				}
			}
			if( allAdded ) { ret = true; }
		}
		return ret;
	}
	public void addFaceIndices( int[] faceIndices ) {
		for( int v : faceIndices ) {
			mIndexList.add( Integer.valueOf(v));
		}
	}
	public void createPalette() {
		int boneCount = 0;
		for( int v : mBoneList ) {
			if( v >= 0 ) {
				boneCount++;
			}
		}
		int[] palette = new int[ boneCount];
		boneCount = 0;
		for( int v : mBoneList ) {
			if( v >= 0 ) {
				palette[boneCount++] = v;
			}
		}
		mBoneList = palette;
	}
	public void setBoneMappingTable( int[] tbl ) { mBoneMappingTable = tbl; }
	// ボーンのID -> パレットでの番号にするためのテーブル.
	public int[] getBoneMappingTable() { return mBoneMappingTable; }
	public int[] getBonePalette() {
		return mBoneList;
	}
	public void createIndexBuffer() {
		int[] vbo = new int[1];
		GLES20.glGenBuffers(1, vbo, 0 );
		mIndexVBO = vbo[0];
		int[] indices = new int[ mIndexList.size() ];
		for( int i = 0; i < mIndexList.size(); ++i ) {
			indices[i] = mIndexList.get(i).intValue();
		}
		IntBuffer ib = ByteBuffer.allocateDirect( 4 * mIndexList.size() ).order(ByteOrder.nativeOrder()).asIntBuffer();
		ib.put(indices).position(0);
		GLES20.glBindBuffer( GLES20.GL_ELEMENT_ARRAY_BUFFER, mIndexVBO );
		GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, ib.capacity()*4, ib, GLES20.GL_STATIC_DRAW );
		ib = null;
	}
	public void createWeightIndicesBuffer( MmdSkinInfo[] skininfo, int totalVertexCount ) {
		byte[] blendInfo = new byte[4 * totalVertexCount];
		for( Integer v : mIndexList ) {
			int index = v.intValue();
			int indexAinPalette = mBoneMappingTable[ skininfo[ index ].boneA ];
			int indexBinPalette = mBoneMappingTable[ skininfo[ index ].boneB ];
			
			int blendPos = index*4;
			blendInfo[blendPos+0] = (byte)indexAinPalette;
			blendInfo[blendPos+1] = (byte)indexBinPalette;
			blendInfo[blendPos+2] = (byte)( skininfo[index].weight * 255 );
			blendInfo[blendPos+3] = (byte)( 255 - (skininfo[index].weight * 255) );
		}
		int[] vbo = new int[1];
		GLES20.glGenBuffers(1, vbo, 0);
		mVertexBlendVBO = vbo[0];
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBlendVBO );
		ByteBuffer bb = ByteBuffer.allocateDirect(blendInfo.length);
		bb.put( blendInfo ).position(0);
		GLES20.glBufferData( GLES20.GL_ARRAY_BUFFER, bb.capacity(), bb, GLES20.GL_STATIC_DRAW);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0 );
	}
	public int getIndexBuffer() { return mIndexVBO; }
	public int getBlendBuffer() { return mVertexBlendVBO; }
	public int getIndexCount() { return mIndexList.size(); }

	private ArrayList<Integer> mIndexList = new ArrayList<Integer>(); // このセグメントに属する頂点インデクス.
	private int[] mBoneList;
	private int[] mBoneMappingTable;
	
	private int mIndexVBO;
	private int mVertexBlendVBO;
}
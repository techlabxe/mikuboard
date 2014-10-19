package com.techlabxe.mikuboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import android.util.Log;

public class MMDAnimResource {
	public MMDAnimResource( InputStream in ) {
		readVMD( in );
	}
	
	public int getAnimationPeriod() { return mAnimationPeriod; }
	public AnimNode getAnimNode( String boneName ) {
		return mAnimation.get(boneName);
	}
	
	private void readVMD( InputStream in ) {
		try {
			MMDDataReader reader = new MMDDataReader(in);
			reader.readAscii( 30 ); // header.
			String name = reader.readAscii(20);

			int vmdMotionCount = reader.readInt();
			HashSet<String> boneList = new HashSet<String>();
			int maxKeyframeCount = -1;

			KeyFrame[] keyframeList = new KeyFrame[vmdMotionCount];
			for( int i = 0; i < vmdMotionCount; ++i ) {
				String boneName = reader.readAscii(15);
				
				int frameNo = reader.readInt();
				float[] location = new float[3];
				float[] rotation = new float[4];
				for( int  j = 0; j < location.length; ++j ) {
					location[j] = reader.readFloat();
				}
				for( int j = 0; j < rotation.length; ++j ) {
					rotation[j] = reader.readFloat();
				}
				location[2] *= -1.0f;
				rotation[2] *= -1.0f;
				rotation[3] *= -1.0f;
				reader.readAscii(64); //  interporation. “Ç‚Ý”ò‚Î‚µ.
				
				maxKeyframeCount = maxKeyframeCount < frameNo ? frameNo : maxKeyframeCount;
				keyframeList[i] = new KeyFrame( frameNo, boneName, location, rotation ) ;
				AnimNode animNode = null;
				if( mAnimation.containsKey(boneName) == false ) {
					animNode = new AnimNode(boneName);
					mAnimation.put( boneName, animNode );
				} else {
					animNode = mAnimation.get(boneName);
				}
				boneList.add(boneName);
			}
			for( KeyFrame frame : keyframeList ) {
				String boneName = frame.getName();
				AnimNode node = mAnimation.get(boneName);
				if( node != null ) {
					node.addKeyFrame( frame );
				}
			}
			for( String boneName : boneList ) {
				AnimNode node = mAnimation.get(boneName);
				if( node != null ) {
					node.sortFrameList();
				}
			}
			mAnimationPeriod = maxKeyframeCount;
		} catch(IOException e ) {
			
		}
	}
	
	private int mAnimationPeriod;
	private HashMap<String, AnimNode> mAnimation = new HashMap<String, AnimNode>();
}


class KeyFrame {
	public KeyFrame( int frameNo, String name, float[] trans, float[] quat ) { 
		mFrameNo = frameNo;
		mTranslation = trans;
		mRotationQuaternion = quat;
		mName = name;
	}
	public String getName() { return mName; }
	
	public int  getFrameNumber() { return mFrameNo; }
	public MmdVec3 getTranslation() {return new MmdVec3(mTranslation[0],mTranslation[1],mTranslation[2]);}
	public float[] getQuaternion() { return mRotationQuaternion; }
	
	public static void QuaternionSlerp( float[] result, float[] a, float[] b, float rate ) {
		float diff = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
		float abs_diff = Math.abs(diff);
		float startWeight = 1.0f - rate;
		float finishWeight= rate;
		if(1.0f - abs_diff > 0.01f ) {
			float theta = (float)Math.acos( abs_diff );
			float oneOverSinTheta = (float)(1.0f / Math.sin(theta));
			startWeight = (float)(Math.sin(theta * (1.0f-rate)) * oneOverSinTheta );
			finishWeight= (float)(Math.sin(theta * rate) * oneOverSinTheta );
			if( diff < 0.0f ) {
				startWeight = -startWeight;
			}
		}
		result[0] = (a[0] * startWeight) + (finishWeight * b[0]);
		result[1] = (a[1] * startWeight) + (finishWeight * b[1]);
		result[2] = (a[2] * startWeight) + (finishWeight * b[2]);
		result[3] = (a[3] * startWeight) + (finishWeight * b[3]);
	}
	
	private int mFrameNo;
	private String mName;
	private float[] mTranslation;
	private float[] mRotationQuaternion;
}
class KeyFrameComparator implements Comparator<KeyFrame> {
	@Override
	public int compare(KeyFrame arg0, KeyFrame arg1) {
		return arg0.getFrameNumber() - arg1.getFrameNumber();
	}
	
}

class AnimNode {
	public AnimNode( String name ) {
		mNodeName = name;
	}
	public String getName() { return mNodeName; }
	public void addKeyFrame( KeyFrame frame ) {
		mFrameList.add(frame);
	}

	public void calcAnimation( int frameNo, float[] translation, float[] rotation ) {
		int prev = -1, next = -1;
		for( int i = 0; i < mFrameList.size(); ++i ) {
			KeyFrame frame = mFrameList.get(i);
			if( frame.getFrameNumber() <= frameNo) {
				prev = i;
			} else {
				break;
			}
		}
		if( prev < 0 ) {
			// NotFound.
			translation[0] = 0.0f; translation[1] = 0.0f; translation[2] = 0.0f;
			rotation[0] = 0.0f; rotation[1] = 0.0f; rotation[2] = 0.0f; rotation[3] = 1.0f;
			Log.i("VRDEBUG", "prev not found." );
			return;
		}
		next = prev+1;
		if( next >= mFrameList.size() ) {
			KeyFrame f =mFrameList.get(prev); 
			MmdVec3 t = f.getTranslation();
			translation[0] = t.x;
			translation[1] = t.y;
			translation[2] = t.z;
			
			float[] q = f.getQuaternion();
			rotation[0] = q[0];
			rotation[1] = q[1];
			rotation[2] = q[2];
			rotation[3] = q[3];
			return;
		}
		KeyFrame f1 = mFrameList.get(prev);
		KeyFrame f2 = mFrameList.get(next);
		if( f1 == null || f2 == null ) {
			Log.e( "VRDEBUG", "ERROR f1 or f2 is null object" );
		}
		float rate = (frameNo - f1.getFrameNumber()) / (float)(f2.getFrameNumber() - f1.getFrameNumber());
		MmdVec3 t1 = f1.getTranslation();
		MmdVec3 t2 = f2.getTranslation();
		translation[0] = rate * ( t2.x - t1.x ) + t1.x;
		translation[1] = rate * (t2.y - t1.y ) + t1.y;
		translation[2] = rate * (t2.z - t1.z ) + t1.z;
		
		float[] q1 = f1.getQuaternion();
		float[] q2 = f2.getQuaternion();
		KeyFrame.QuaternionSlerp(rotation, q1, q2, rate);
	}
	public int getFrameListCount() {
		return mFrameList.size();
	}
	public void sortFrameList() {
		Collections.sort( mFrameList, new KeyFrameComparator() );
	}
	private String mNodeName;
	private ArrayList<KeyFrame> mFrameList = new ArrayList<KeyFrame>();
}

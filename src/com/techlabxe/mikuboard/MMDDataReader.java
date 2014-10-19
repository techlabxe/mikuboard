package com.techlabxe.mikuboard;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MMDDataReader {
	public MMDDataReader( InputStream inStream ) throws IOException {
		int file_length = inStream.available();
		if( file_length < 1 ) {
			file_length = 2 * 1024 * 1024;
		}
		byte[] buf = new byte[file_length];
		int buf_len = inStream.read( buf, 0, file_length );
		_buffer = ByteBuffer.wrap( buf, 0, buf_len );
		_buffer.order( ByteOrder.LITTLE_ENDIAN );
	}
	public int readByte() {
		return _buffer.get();
	}
	public int read() {
		int v= _buffer.get(); v += (v<0) ? 0xFF : 0;
		return v;
	}
	public short readShort() {
		return _buffer.getShort();
	}
	public int readUShort() {
		int v = _buffer.getShort(); 
		v += (v<0) ? 0xFFFF : 0;
		return v;
	}
	public int readInt() {
		return _buffer.getInt();
	}
	public float readFloat() {
		return _buffer.getFloat();
	}
	public String readAscii( int length ) {
		String ret = "";
		try {
			int len = 0;
			byte[] tmp = new byte[length];
			int i = 0;
			for( i = 0; i < length; ++i ) {
				byte b = _buffer.get();
				if( b == 0 ) {
					i++;
					break;
				}
				tmp[i] = b;
				len++;
			}
			ret = new String( tmp, 0, len, "Shift_JIS");
			for( ;i < length; ++i ) {
				_buffer.get();
			}
		} catch(UnsupportedEncodingException e ) {
			
		}
		return ret;
	}
	public MmdVec2 readVec2() {
		float x, y;
		x = readFloat(); y = readFloat();
		return new MmdVec2(x,y);
	}
	public MmdVec3 readVec3() {
		float x, y ,z;
		x = readFloat(); y = readFloat(); z = readFloat();
		return new MmdVec3(x,y,z);
	}
	public void readBytes( byte[] out ) {
		_buffer.get(out);
	}
	
	private ByteBuffer _buffer;
}

class MmdVec2 {
	public float x, y;
	public MmdVec2() { x = 0.0f; y = 0.0f; }
	public MmdVec2( float ix, float iy ) { x = ix; y = iy; }
	public void set( float ix, float iy ) { x = ix; y = iy; }
	public void add( MmdVec2 a, MmdVec2 b ) {
		x = a.x + b.x; y = a.y + b.y;
	}
	public void add( MmdVec2 a ) { x += a.x; y += a.y; }
	public void sub( MmdVec2 a, MmdVec2 b ) {
		x = a.x - b.x; y = a.y - b.y;
	}
	public void normalize() {
		double len = Math.sqrt( x * x + y * y );
		x /= len; y /= len;
	}
	public static MmdVec2[] createArray( int num ) {
		MmdVec2[] ret = new MmdVec2[num];
		for( int i = 0; i < num; ++i ) {
			ret[i] = new MmdVec2();
		}
		return ret;
	}
}
class MmdVec3 {
	public float x, y, z;
	public MmdVec3() {
		x = 0.0f; y = 0.0f; z = 0.0f;
	}
	public MmdVec3( float ix, float iy, float iz ) {
		x = ix; y = iy; z =iz;
	}
	public void set( float ix, float iy, float iz ) {
		x = ix; y = iy; z =iz;
	}
	public void add( MmdVec3 a, MmdVec3 b ) {
		x = a.x + b.x; y = a.y + b.y; z = a.z + b.z;
	}
	public void add( MmdVec3 a ) {
		x += a.x; y += a.y; z += a.z;
	}
	public void sub( MmdVec3 a, MmdVec3 b ) {
		x = a.x - b.x; y = a.y - b.y; z = a.z - b.z;
	}
	public double dot( MmdVec3 v ) {
		return x * v.x + y * v.y + z * v.z;
	}
	public void normalize() {
		double len = Math.sqrt( x * x + y * y + z * z );
		x /= len; y /= len;	z /= len;
	}
	public static MmdVec3[] createArray( int num ) {
		MmdVec3[] ret = new MmdVec3[num];
		for( int i = 0; i < num; ++i ) {
			ret[i] = new MmdVec3();
		}
		return ret;
	}
}

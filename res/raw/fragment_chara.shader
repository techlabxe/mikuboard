precision mediump float;

uniform vec4 u_useTexture;
uniform sampler2D texture;
varying vec4 v_Color;
varying vec2 v_UV;

void main() 
{
	if( u_useTexture.x > 0.5 ) {
		gl_FragColor = v_Color * texture2D( texture, v_UV.xy );
	} else {
		gl_FragColor = v_Color;
	}
}

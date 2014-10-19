attribute vec4 a_Position;
attribute vec4 a_Color;
attribute vec3 a_Normal;

varying vec3 v_Grid;
varying vec4 v_Color;

uniform mat4 u_MVP;
uniform mat4 u_World;
uniform mat4 u_MVMatrix;
uniform vec3 u_LightPos;

void main() 
{
    vec3 modelVertex = vec3( u_World * a_Position );
    vec3 modelViewVertex = vec3( u_MVMatrix * a_Position );
    vec3 modelViewNormal = vec3( u_MVMatrix * vec4(a_Normal, 0.0) );
    v_Grid = modelVertex;
    float distance = length( u_LightPos - modelViewVertex );
    vec3 lightVector = normalize( u_LightPos - modelViewVertex );
    float diffuse = max( dot(modelViewNormal, lightVector), 0.5 );
    v_Color = a_Color * diffuse;
	gl_Position = u_MVP * a_Position; 
}

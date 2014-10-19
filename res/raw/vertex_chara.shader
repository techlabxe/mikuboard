attribute vec4 a_Position;
attribute vec3 a_Normal;
attribute vec2 a_UV;
attribute vec4 a_Blend;

varying vec3 v_Grid;
varying vec4 v_Color;
varying vec2 v_UV;

uniform mat4 u_mtxVP;
uniform mat4 u_World;
uniform mat4 u_MVMatrix;
uniform vec3 u_LightPos;
uniform vec4 u_diffuseColor;
uniform mat4 u_BoneMatrices[32];

void main() 
{
    vec3 position = vec3(0.0,0.0,0.0);
    vec3 normal = vec3(0.0,0.0,0.0);
    position += vec3( u_BoneMatrices[ int(a_Blend.x*255.01) ] * a_Position ) * a_Blend.z;
    position += vec3( u_BoneMatrices[ int(a_Blend.y*255.01) ] * a_Position ) * (1.0 -  a_Blend.z);
    normal += vec3( u_BoneMatrices[ int(a_Blend.x*255.01) ] * vec4(a_Normal, 0.0) ) * a_Blend.z;
    normal += vec3( u_BoneMatrices[ int(a_Blend.y*255.01) ] * vec4(a_Normal, 0.0) ) * (1.0-a_Blend.z);

    vec3 modelVertex = position; 
    vec3 modelViewVertex = vec3( u_MVMatrix * a_Position );

    v_Grid = modelVertex;
    float distance = length( u_LightPos - modelViewVertex );
    vec3 lightVector = normalize( u_LightPos - modelViewVertex );
    float diffuse = max( dot(normal, lightVector), 0.5 );
    v_Color = diffuse * u_diffuseColor;
    //v_Color = vec4(a_Normal * 0.5 + 0.5, 1.0);
    v_UV = a_UV;
	gl_Position = u_mtxVP * vec4( position, 1.0); 
}

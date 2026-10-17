package dev.tacticalcombat.client.dicevisual;

/** CPU-side 3D orientation. The chosen server face normal is aligned to the viewer at rest. */
public final class DieOrientation {
    public static double[] vector(PolyhedralMesh.Vec v){return new double[]{v.x(),v.y(),v.z()};}
    public static double[] mul(double[][] m,double[] v){return new double[]{m[0][0]*v[0]+m[0][1]*v[1]+m[0][2]*v[2],m[1][0]*v[0]+m[1][1]*v[1]+m[1][2]*v[2],m[2][0]*v[0]+m[2][1]*v[1]+m[2][2]*v[2]};}
    public static double[][] align(PolyhedralMesh.Vec n){double[] a=vector(n);double[] v={a[1],-a[0],0};double c=a[2];if(c<-.999)return new double[][]{{-1,0,0},{0,1,0},{0,0,-1}};double k=1/(1+c);return new double[][]{{1-(v[1]*v[1]+v[2]*v[2])*k,-v[2]+v[0]*v[1]*k,v[1]+v[0]*v[2]*k},{v[2]+v[0]*v[1]*k,1-(v[0]*v[0]+v[2]*v[2])*k,-v[0]+v[1]*v[2]*k},{-v[1]+v[0]*v[2]*k,v[0]+v[1]*v[2]*k,1-(v[0]*v[0]+v[1]*v[1])*k}};}
    public static double[][] rotation(double x,double y,double z){double sx=Math.sin(x),cx=Math.cos(x),sy=Math.sin(y),cy=Math.cos(y),sz=Math.sin(z),cz=Math.cos(z);return new double[][]{{cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx},{sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx},{-sy,cy*sx,cy*cx}};}
    private DieOrientation(){}
}

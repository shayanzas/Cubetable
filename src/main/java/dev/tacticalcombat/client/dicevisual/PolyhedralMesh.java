package dev.tacticalcombat.client.dicevisual;

import java.util.*;

/** Convex unit polyhedra. Immutable meshes; face order is a presentation mapping only. */
public final class PolyhedralMesh {
    public record Vec(double x,double y,double z){
        public Vec add(Vec b){return new Vec(x+b.x,y+b.y,z+b.z);} public Vec sub(Vec b){return new Vec(x-b.x,y-b.y,z-b.z);}
        public Vec mul(double n){return new Vec(x*n,y*n,z*n);} public double dot(Vec b){return x*b.x+y*b.y+z*b.z;}
        public Vec cross(Vec b){return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);} public Vec unit(){double n=Math.sqrt(dot(this));return mul(1/n);}
    }
    public record Mesh(List<Vec> vertices,List<int[]> faces){
        public Mesh {vertices=List.copyOf(vertices);faces=faces.stream().map(int[]::clone).toList();}
        @Override public List<int[]> faces(){return faces.stream().map(int[]::clone).toList();}
        public int faceCount(){return faces.size();}
        public Vec center(int index){int[] f=faces.get(index);Vec v=new Vec(0,0,0);for(int id:f)v=v.add(vertices.get(id));return v.mul(1d/f.length);}
        public Vec normal(int index){return normalOf(vertices,faces.get(index));}
        public int edgeCount(){var edges=new HashSet<String>();for(var f:faces)for(int i=0;i<f.length;i++){int a=f[i],b=f[(i+1)%f.length];edges.add(Math.min(a,b)+":"+Math.max(a,b));}return edges.size();}
    }
    private static Vec normalOf(List<Vec> vertices,int[] f){return vertices.get(f[1]).sub(vertices.get(f[0])).cross(vertices.get(f[2]).sub(vertices.get(f[0]))).unit();}
    private static Mesh mesh(List<Vec> raw,List<int[]> input){double radius=raw.stream().mapToDouble(v->Math.sqrt(v.dot(v))).max().orElseThrow();var points=raw.stream().map(v->v.mul(1/radius)).toList();var faces=new ArrayList<int[]>();for(int[] f:input){int[] v=f.clone();if(normalOf(points,v).dot(center(points,v))<0)reverse(v);faces.add(v);}return new Mesh(points,faces);}
    private static Vec center(List<Vec> p,int[] f){Vec c=new Vec(0,0,0);for(int i:f)c=c.add(p.get(i));return c.mul(1d/f.length);}
    private static void reverse(int[] a){for(int i=0,j=a.length-1;i<j;i++,j--){int t=a[i];a[i]=a[j];a[j]=t;}}
    private static List<int[]> hullTriangles(List<Vec> v){var out=new ArrayList<int[]>();double nearest=Double.POSITIVE_INFINITY;for(int i=0;i<v.size();i++)for(int j=i+1;j<v.size();j++)nearest=Math.min(nearest,v.get(i).sub(v.get(j)).dot(v.get(i).sub(v.get(j))));
        for(int i=0;i<v.size();i++)for(int j=i+1;j<v.size();j++)for(int k=j+1;k<v.size();k++)if(near(v.get(i),v.get(j),nearest)&&near(v.get(j),v.get(k),nearest)&&near(v.get(k),v.get(i),nearest))out.add(new int[]{i,j,k});return out;}
    private static boolean near(Vec a,Vec b,double value){Vec d=a.sub(b);return Math.abs(d.dot(d)-value)<1e-6;}
    private static Mesh tetra(){var p=List.of(new Vec(1,1,1),new Vec(1,-1,-1),new Vec(-1,1,-1),new Vec(-1,-1,1));return mesh(p,List.of(new int[]{0,1,2},new int[]{0,1,3},new int[]{0,2,3},new int[]{1,2,3}));}
    private static Mesh cube(){var p=new ArrayList<Vec>();for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1})p.add(new Vec(x,y,z));return mesh(p,List.of(new int[]{0,1,3,2},new int[]{4,6,7,5},new int[]{0,4,5,1},new int[]{2,3,7,6},new int[]{0,2,6,4},new int[]{1,5,7,3}));}
    private static Mesh octa(){var p=List.of(new Vec(1,0,0),new Vec(-1,0,0),new Vec(0,1,0),new Vec(0,-1,0),new Vec(0,0,1),new Vec(0,0,-1));return mesh(p,hullTriangles(p));}
    private static Mesh ten(){double h=.12,pole=2*h/(1-Math.cos(Math.PI/5))-h;var p=new ArrayList<Vec>();p.add(new Vec(0,0,pole));p.add(new Vec(0,0,-pole));for(int i=0;i<10;i++){double angle=i*Math.PI/5;p.add(new Vec(Math.cos(angle),Math.sin(angle),i%2==0?h:-h));}var f=new ArrayList<int[]>();for(int i=0;i<5;i++){f.add(new int[]{0,2+2*i,2+(2*i+1)%10,2+(2*i+2)%10});f.add(new int[]{1,2+(2*i+1)%10,2+(2*i+2)%10,2+(2*i+3)%10});}return mesh(p,f);}
    private static Mesh ico(){double g=(1+Math.sqrt(5))/2;var p=new ArrayList<Vec>();for(int a:new int[]{-1,1})for(int b:new int[]{-1,1}){p.add(new Vec(0,a,b*g));p.add(new Vec(a,b*g,0));p.add(new Vec(b*g,0,a));}return mesh(p,hullTriangles(p));}
    private static Mesh dodeca(){Mesh ico=ico();var source=ico.vertices();var tri=ico.faces();var v=new ArrayList<Vec>();for(int[] f:tri)v.add(center(source,f).unit());var pent=new ArrayList<int[]>();for(int i=0;i<source.size();i++){Vec axis=source.get(i).unit(),u=Math.abs(axis.z())<.9?axis.cross(new Vec(0,0,1)).unit():axis.cross(new Vec(0,1,0)).unit(),w=axis.cross(u);var adjacent=new ArrayList<Integer>();for(int j=0;j<tri.size();j++)for(int k:tri.get(j))if(k==i){adjacent.add(j);break;}adjacent.sort(Comparator.comparingDouble(j->Math.atan2(v.get(j).dot(w),v.get(j).dot(u))));pent.add(adjacent.stream().mapToInt(Integer::intValue).toArray());}return mesh(v,pent);}
    private static final Map<Integer,Mesh> CACHE=Map.of(4,tetra(),6,cube(),8,octa(),10,ten(),12,dodeca(),20,ico());
    public static Mesh forSides(int sides){Mesh m=CACHE.get(sides);if(m==null)throw new IllegalArgumentException("Unsupported die geometry");return m;}
    private PolyhedralMesh(){}
}

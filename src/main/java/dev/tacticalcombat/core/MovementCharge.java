package dev.tacticalcombat.core;

/** Charge accepted grid cost in proportion to distance actually traversed along its fixed route. */
public final class MovementCharge {
    private final double cost, length;
    private double travelled, charged;
    public MovementCharge(double cost, double length) {
        if (!Double.isFinite(cost) || !Double.isFinite(length) || cost < 0 || length <= 0)
            throw new IllegalArgumentException("Invalid route accounting");
        this.cost = cost; this.length = length;
    }
    public double advance(double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid progress");
        travelled = Math.min(length, travelled + distance);
        double next = cost * travelled / length, delta = next - charged;
        charged = next;
        return delta;
    }
}

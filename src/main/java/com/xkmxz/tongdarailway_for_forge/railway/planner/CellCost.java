package com.xkmxz.tongdarailway_for_forge.railway.planner;

public class CellCost {
    public final int height;
    public final double obstacleCost;
    public final boolean blocked;

    public CellCost(int height, double obstacleCost, boolean blocked) {
        this.height = height;
        this.obstacleCost = obstacleCost;
        this.blocked = blocked;
    }
}

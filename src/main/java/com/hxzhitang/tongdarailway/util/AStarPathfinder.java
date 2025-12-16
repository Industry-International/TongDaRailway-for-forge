package com.hxzhitang.tongdarailway.util;

import com.hxzhitang.tongdarailway.railway.RegionPos;

import java.util.*;

import static com.hxzhitang.tongdarailway.Tongdarailway.CHUNK_GROUP_SIZE;
import static com.hxzhitang.tongdarailway.railway.RailwayMap.samplingNum;

// Reference: https://www.redblobgames.com/pathfinding/a-star/introduction.html
public class AStarPathfinder {

    // Possible movement directions: up, down, left, right, diagonals
    private static final int[][] DIRECTIONS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},    // Cardinal
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}    // Diagonal
    };

    // Movement cost: cardinal is 1, diagonal is sqrt(2) ≈ 1.414
    private static final double[] MOVEMENT_COST = {
            1.0, 1.0, 1.0, 1.0,                  // Cardinal
            1.414, 1.414, 1.414, 1.414           // Diagonal
    };

    public static List<int[]> findPath(int[][] image, int[] start, int[] end, AdditionalCostFunction additionalCostFunction) {
        if (image == null || image.length == 0 || image[0].length == 0) {
            return new ArrayList<>();
        }

        int rows = image.length;
        int cols = image[0].length;

        // Verify start and end are within image bounds
        if (!isValidCoordinate(start[0], start[1], rows, cols) ||
                !isValidCoordinate(end[0], end[1], rows, cols)) {
            return new ArrayList<>();
        }

        // Priority queue sorted by f value
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));

        // Record g value for each node (actual cost from start)
        double[][] gScore = new double[rows][cols];
        for (double[] row : gScore) {
            Arrays.fill(row, Double.MAX_VALUE);
        }

        // Record parent node for path reconstruction
        Node[][] cameFrom = new Node[rows][cols];

        // Initialize start node
        Node startNode = new Node(start[0], start[1]);
        startNode.g = 0;
        startNode.h = heuristic(start, end);
        startNode.f = startNode.g + startNode.h;

        gScore[start[0]][start[1]] = 0;
        openSet.offer(startNode);

        // Track nodes in open set
        boolean[][] inOpenSet = new boolean[rows][cols];
        inOpenSet[start[0]][start[1]] = true;

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            int currentX = current.x;
            int currentY = current.y;

            // Reached end, reconstruct path
            if (currentX == end[0] && currentY == end[1]) {
                return reconstructPath(cameFrom, current);
            }

            inOpenSet[currentX][currentY] = false;

            // Explore all possible movements
            for (int i = 0; i < DIRECTIONS.length; i++) {
                int[] direction = DIRECTIONS[i];
                int newX = currentX + direction[0];
                int newY = currentY + direction[1];

                // Check if coordinate is valid
                if (!isValidCoordinate(newX, newY, rows, cols)) {
                    continue;
                }

                // Calculate movement cost
                double movementCost = MOVEMENT_COST[i];
                double pixelCost = Math.abs(image[currentX][currentY] - image[newX][newY]);
                double tentativeG = current.g + movementCost + pixelCost + additionalCostFunction.cost(currentX, currentY);

                // Found better path
                if (tentativeG < gScore[newX][newY]) {
                    Node neighbor = new Node(newX, newY);
                    neighbor.g = tentativeG;
                    neighbor.h = heuristic(new int[]{newX, newY}, end);
                    neighbor.f = neighbor.g + neighbor.h;

                    cameFrom[newX][newY] = current;
                    gScore[newX][newY] = tentativeG;

                    if (!inOpenSet[newX][newY]) {
                        openSet.offer(neighbor);
                        inOpenSet[newX][newY] = true;
                    }
                }
            }
        }

        // Open set empty without reaching end, no path
        return new ArrayList<>();
    }

    // Check if coordinate is valid
    private static boolean isValidCoordinate(int x, int y, int rows, int cols) {
        return x >= 0 && x < rows && y >= 0 && y < cols;
    }

    // Heuristic function using Euclidean distance
    private static double heuristic(int[] a, int[] b) {
        int dx = Math.abs(a[0] - b[0]);
        int dy = Math.abs(a[1] - b[1]);
        return Math.sqrt(dx * dx + dy * dy);
    }

    // Reconstruct path
    private static List<int[]> reconstructPath(Node[][] cameFrom, Node current) {
        List<int[]> path = new ArrayList<>();

        // Trace back from end to start
        while (current != null) {
            path.add(0, new int[]{current.x, current.y});
            current = cameFrom[current.x][current.y];
        }

        return path;
    }

    // Node class
    static class Node {
        int x, y;
        double g; // Actual cost from start
        double h; // Heuristic cost estimate
        double f; // Total cost f = g + h

        Node(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // Additional cost function interface
    @FunctionalInterface
    public interface AdditionalCostFunction {
        double cost(int x, int y);
    }

    public static int[] world2PicPos(int[] worldPos, RegionPos centerRegionPos) {
        int wx = worldPos[0];
        int wz = worldPos[1];
        return new int[]{
                (wx - (centerRegionPos.x() - 1) * CHUNK_GROUP_SIZE * 16) * samplingNum / 16,
                (wz - (centerRegionPos.z() - 1) * CHUNK_GROUP_SIZE * 16) * samplingNum / 16
        };
    }

    public static int[] pic2RegionPos(int[] picPos) {
        int px = picPos[0];
        int pz = picPos[1];
        return new int[]{
                px - CHUNK_GROUP_SIZE * samplingNum,
                pz - CHUNK_GROUP_SIZE * samplingNum,
                picPos[2]
        };
    }
}

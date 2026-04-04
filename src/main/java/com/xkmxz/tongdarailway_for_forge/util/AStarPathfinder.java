package com.xkmxz.tongdarailway_for_forge.util;

import com.xkmxz.tongdarailway_for_forge.railway.RegionPos;

import java.util.*;

import com.xkmxz.tongdarailway_for_forge.Config;
import static com.xkmxz.tongdarailway_for_forge.railway.RailwayMap.samplingNum;

/**
 * A*路径查找算法实现
 * 参考: https://www.redblobgames.com/pathfinding/a-star/introduction.html
 */
public class AStarPathfinder {

    /**
     * 可能的移动方向: 上下左右和四个对角线方向
     * 前四个是基本方向(正交方向)，后四个是对角线方向
     */
    private static final int[][] DIRECTIONS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},    // 正交方向: 左、右、上、下
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}    // 对角线方向
    };

    /**
     * 移动代价: 正交方向为0.8(优先选择)，对角线方向为sqrt(2)≈1.414
     * 降低正交方向的代价以鼓励生成更直的线路
     */
    private static final double[] MOVEMENT_COST = {
            0.8, 0.8, 0.8, 0.8,                  // 正交方向(降低代价以鼓励直线)
            1.414, 1.414, 1.414, 1.414           // 对角线方向
    };

    /**
     * 查找路径
     * @param image 高度图
     * @param start 起点坐标 [x, y]
     * @param end 终点坐标 [x, y]
     * @param additionalCostFunction 额外代价函数
     * @return 路径点列表
     */
    public static List<int[]> findPath(int[][] image, int[] start, int[] end, AdditionalCostFunction additionalCostFunction) {
        if (image == null || image.length == 0 || image[0].length == 0) {
            return new ArrayList<>();
        }

        int rows = image.length;
        int cols = image[0].length;

        // 验证起点和终点是否在有效范围内
        if (!isValidCoordinate(start[0], start[1], rows, cols) ||
                !isValidCoordinate(end[0], end[1], rows, cols)) {
            return new ArrayList<>();
        }

        // 优先队列，按f值排序
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));

        // 记录每个节点的g值(从起点到该节点的实际代价)
        double[][] gScore = new double[rows][cols];
        for (double[] row : gScore) {
            Arrays.fill(row, Double.MAX_VALUE);
        }

        // 记录父节点，用于路径重建
        Node[][] cameFrom = new Node[rows][cols];

        // 初始化起点
        Node startNode = new Node(start[0], start[1]);
        startNode.g = 0;
        startNode.h = heuristic(start, end);
        startNode.f = startNode.g + startNode.h;

        gScore[start[0]][start[1]] = 0;
        openSet.offer(startNode);

        // 标记节点是否在开放集合中
        boolean[][] inOpenSet = new boolean[rows][cols];
        inOpenSet[start[0]][start[1]] = true;

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            int currentX = current.x;
            int currentY = current.y;

            // 到达终点，重建路径
            if (currentX == end[0] && currentY == end[1]) {
                return reconstructPath(cameFrom, current);
            }

            inOpenSet[currentX][currentY] = false;

            // 探索所有可能的移动方向
            for (int i = 0; i < DIRECTIONS.length; i++) {
                int[] direction = DIRECTIONS[i];
                int newX = currentX + direction[0];
                int newY = currentY + direction[1];

                // 检查坐标是否有效
                if (!isValidCoordinate(newX, newY, rows, cols)) {
                    continue;
                }

                // 计算移动代价
                double movementCost = MOVEMENT_COST[i];
                double pixelCost = Math.abs(image[currentX][currentY] - image[newX][newY]);
                double tentativeG = current.g + movementCost + pixelCost + additionalCostFunction.cost(currentX, currentY);

                // 找到更优路径
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

        // 开放集合为空但未到达终点，说明无路径
        return new ArrayList<>();
    }

    /**
     * 检查坐标是否在有效范围内
     * @param x x坐标
     * @param y y坐标
     * @param rows 总行数
     * @param cols 总列数
     * @return 是否有效
     */
    private static boolean isValidCoordinate(int x, int y, int rows, int cols) {
        return x >= 0 && x < rows && y >= 0 && y < cols;
    }

    /**
     * 启发函数，使用欧几里得距离并考虑地形高度
     * 增加直线偏好以减少弯道
     * @param a 起点坐标
     * @param b 终点坐标
     * @return 启发式估计值
     */
    private static double heuristic(int[] a, int[] b) {
        int dx = Math.abs(a[0] - b[0]);
        int dy = Math.abs(a[1] - b[1]);
        
        // 考虑地形高度因素，增加高度差的权重
        int dz = 0;
        if (a.length > 2 && b.length > 2) {
            dz = Math.abs(a[2] - b[2]);
        }
        
        // 高度权重系数，可根据实际情况调整
        double heightWeight = 0.5;
        
        // 计算欧几里得距离
        double euclideanDistance = Math.sqrt(dx * dx + dy * dy);
        
        // 直线偏好: 当dx或dy为0时(即直线移动)，给予额外的奖励
        double straightLineBonus = 0.0;
        if (dx == 0 || dy == 0) {
            straightLineBonus = 0.3; // 直线移动的奖励值
        }
        
        return euclideanDistance + dz * heightWeight - straightLineBonus;
    }

    /**
     * 重建路径
     * @param cameFrom 父节点记录数组
     * @param current 当前节点(终点)
     * @return 路径点列表
     */
    private static List<int[]> reconstructPath(Node[][] cameFrom, Node current) {
        List<int[]> path = new ArrayList<>();

        // 从终点回溯到起点
        while (current != null) {
            path.add(0, new int[]{current.x, current.y});
            current = cameFrom[current.x][current.y];
        }

        return path;
    }

    /**
     * 节点类，用于A*算法
     */
    static class Node {
        int x, y;           // 节点坐标
        double g;           // 从起点到该节点的实际代价
        double h;           // 启发式估计代价
        double f;           // 总代价 f = g + h

        Node(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * 额外代价函数接口
     * 用于在路径规划中考虑额外因素(如障碍物、水域等)
     */
    @FunctionalInterface
    public interface AdditionalCostFunction {
        double cost(int x, int y);
    }

    /**
     * 将世界坐标转换为图片坐标
     * @param worldPos 世界坐标 [wx, wz]
     * @param centerRegionPos 中心区域位置
     * @return 图片坐标 [px, pz]
     */
    public static int[] world2PicPos(int[] worldPos, RegionPos centerRegionPos) {
        int wx = worldPos[0];
        int wz = worldPos[1];
        return new int[]{
                (wx - (centerRegionPos.x() - 1) * Config.chunkGroupSize * 16) * samplingNum / 16,
                (wz - (centerRegionPos.z() - 1) * Config.chunkGroupSize * 16) * samplingNum / 16
        };
    }

    /**
     * 将图片坐标转换为区域坐标
     * @param picPos 图片坐标 [px, pz, py]
     * @return 区域坐标 [rx, rz, ry]
     */
    public static int[] pic2RegionPos(int[] picPos) {
        int px = picPos[0];
        int pz = picPos[1];
        return new int[]{
                px - Config.chunkGroupSize * samplingNum,
                pz - Config.chunkGroupSize * samplingNum,
                picPos[2]
        };
    }
}

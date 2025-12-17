package com.hxzhitang.tongdarailway_for_forge.util;

import java.util.concurrent.*;

public class AdaptiveHeightSampler {
    private final double threshold;
    private final int maxLevel;
    private final int samplesPerNode;
    private QuadTree root;
    private final ExecutorService executor;
    private final HeightFunction heightFunction;
    private CountDownLatch latch = null;
    int count = 0;

    private class QuadTree {
        double minX, minZ, maxX, maxZ;
        int level;
        double minHeight, maxHeight;
        QuadTree[] children;
        boolean isLeaf;
        double[][] heightSamples;

        QuadTree(double minX, double minZ, double maxX, double maxZ, int level) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.level = level;
            this.children = null;
            this.isLeaf = true;
        }
    }

    public AdaptiveHeightSampler(double threshold, int maxLevel, int samplesPerNode, HeightFunction heightFunction) {
        this.threshold = threshold;
        this.maxLevel = maxLevel;
        this.samplesPerNode = samplesPerNode;
        this.heightFunction = heightFunction;
        int coreCount = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(coreCount);
    }

    public void buildQuadTree(double regionSize) throws InterruptedException {
        latch = new CountDownLatch(1);
        addCount();
        root = new QuadTree(0, 0, regionSize, regionSize, 0);
        buildNode(root);
        latch.await();
    }

    private void buildNode(QuadTree node) {
        sampleNode(node);

        if (shouldSplit(node)) {
            splitNode(node);
            processChildrenInParallel(node);
        }
        subCount();
    }

    private void processChildrenInParallel(QuadTree parent) {
        for (int i = 0; i < 4; i++) {
            addCount();
            final QuadTree child = parent.children[i];
            executor.submit(() -> buildNode(child));
        }
    }

    private void sampleNode(QuadTree node) {
        int n = samplesPerNode;
        node.heightSamples = new double[n][n];
        node.minHeight = Double.MAX_VALUE;
        node.maxHeight = Double.MIN_VALUE;

        double stepX = (node.maxX - node.minX) / (n - 1);
        double stepZ = (node.maxZ - node.minZ) / (n - 1);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double x = node.minX + i * stepX;
                double z = node.minZ + j * stepZ;
                double height = getHeight(x, z);

                node.heightSamples[i][j] = height;
                node.minHeight = Math.min(node.minHeight, height);
                node.maxHeight = Math.max(node.maxHeight, height);
            }
        }
    }

    private boolean shouldSplit(QuadTree node) {
        if (node.level >= maxLevel) {
            return false;
        }

        double range = node.maxHeight - node.minHeight;
        return range > threshold;
    }

    private void splitNode(QuadTree node) {
        node.isLeaf = false;
        node.children = new QuadTree[4];

        double midX = (node.minX + node.maxX) / 2.0;
        double midZ = (node.minZ + node.maxZ) / 2.0;
        int nextLevel = node.level + 1;

        node.children[0] = new QuadTree(node.minX, node.minZ, midX, midZ, nextLevel);
        node.children[1] = new QuadTree(midX, node.minZ, node.maxX, midZ, nextLevel);
        node.children[2] = new QuadTree(node.minX, midZ, midX, node.maxZ, nextLevel);
        node.children[3] = new QuadTree(midX, midZ, node.maxX, node.maxZ, nextLevel);
    }

    public double getHeight(double x, double z) {
        return heightFunction.getHeight(x, z);
    }

    public int[][] generateImage(int width, int height) {
        int[][] image = new int[width][height];
        double scaleX = root.maxX / width;
        double scaleZ = root.maxZ / height;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                double worldX = x * scaleX;
                double worldZ = z * scaleZ;
                image[x][z] = (int) getInterpolatedHeight(worldX, worldZ);
            }
        }

        return image;
    }

    private double getInterpolatedHeight(double x, double z) {
        return getHeightFromNode(root, x, z);
    }

    private double getHeightFromNode(QuadTree node, double x, double z) {
        if (node.isLeaf) {
            return bilinearInterpolate(node, x, z);
        }

        double midX = (node.minX + node.maxX) / 2.0;
        double midZ = (node.minZ + node.maxZ) / 2.0;

        int childIndex;
        if (x < midX) {
            if (z < midZ) {
                childIndex = 0;
            } else {
                childIndex = 2;
            }
        } else {
            if (z < midZ) {
                childIndex = 1;
            } else {
                childIndex = 3;
            }
        }

        return getHeightFromNode(node.children[childIndex], x, z);
    }

    private double bilinearInterpolate(QuadTree node, double x, double z) {
        int n = samplesPerNode;
        double stepX = (node.maxX - node.minX) / (n - 1);
        double stepZ = (node.maxZ - node.minZ) / (n - 1);

        double gridX = (x - node.minX) / stepX;
        double gridZ = (z - node.minZ) / stepZ;

        int x1 = (int) Math.floor(gridX);
        int z1 = (int) Math.floor(gridZ);
        int x2 = Math.min(x1 + 1, n - 1);
        int z2 = Math.min(z1 + 1, n - 1);

        x1 = Math.max(0, x1);
        z1 = Math.max(0, z1);

        double dx = gridX - x1;
        double dz = gridZ - z1;

        double h1 = node.heightSamples[x1][z1];
        double h2 = node.heightSamples[x2][z1];
        double h3 = node.heightSamples[x1][z2];
        double h4 = node.heightSamples[x2][z2];

        double interpolated = h1 * (1 - dx) * (1 - dz) +
                h2 * dx * (1 - dz) +
                h3 * (1 - dx) * dz +
                h4 * dx * dz;

        return interpolated;
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void printStatistics() {
        Stats stats = new Stats();
        collectStats(root, stats);

        System.out.println("QTree Statistics Info:");
        System.out.println("Node Count: " + stats.totalNodes);
        System.out.println("Leaf Node Count: " + stats.leafNodes);
        System.out.println("Deep: " + stats.maxDepth);
        System.out.println("Sample Point Count: " + stats.totalSamples);
    }

    private void collectStats(QuadTree node, Stats stats) {
        stats.totalNodes++;
        stats.maxDepth = Math.max(stats.maxDepth, node.level);
        stats.totalSamples += samplesPerNode * samplesPerNode;

        if (node.isLeaf) {
            stats.leafNodes++;
        } else {
            for (QuadTree child : node.children) {
                collectStats(child, stats);
            }
        }
    }

    private synchronized void addCount() {
        count++;
    }

    private synchronized void subCount() {
        count--;
        if (count == 0)
            latch.countDown();
    }

    private static class Stats {
        int totalNodes = 0;
        int leafNodes = 0;
        int maxDepth = 0;
        int totalSamples = 0;
    }

    @FunctionalInterface
    public interface HeightFunction {
        double getHeight(double x, double z);
    }
}

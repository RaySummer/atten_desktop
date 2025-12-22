package com.ray.atten.desktop.utils;

import javafx.scene.Cursor;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

public class ImageCropperTool {
    private final Pane container;
    private final Rectangle cropRect; // 裁剪框（透明中心，白边框）
    private final Path maskPath;      // 镂空遮罩层
    private final Rectangle[] handles = new Rectangle[4]; // 四个角的手柄

    private double mouseX, mouseY;
    private final double HANDLE_SIZE = 8.0;

    public ImageCropperTool(Pane container) {
        this.container = container;

        // 1. 初始化半透明遮罩
        maskPath = new Path();
        maskPath.setFill(Color.rgb(0, 0, 0, 0.6)); // 60% 透明度的黑色
        maskPath.setStroke(null);
        maskPath.setFillRule(FillRule.EVEN_ODD); // 关键：奇偶规则实现镂空
        maskPath.setMouseTransparent(true); // 遮罩不阻挡鼠标点击

        // 2. 初始化裁剪框
        cropRect = new Rectangle(200, 100, 200, 200);
        cropRect.setStroke(Color.WHITE);
        cropRect.setStrokeWidth(2);
        cropRect.setFill(Color.TRANSPARENT); // 内部完全透明
        cropRect.setCursor(Cursor.MOVE);

        // 3. 初始化四个角的手柄
        for (int i = 0; i < 4; i++) {
            handles[i] = new Rectangle(HANDLE_SIZE, HANDLE_SIZE, Color.WHITE);
            handles[i].setStroke(Color.BLACK);
            setupHandleEvent(handles[i], i);
        }
        handles[0].setCursor(Cursor.NW_RESIZE); // 左上
        handles[1].setCursor(Cursor.NE_RESIZE); // 右上
        handles[2].setCursor(Cursor.SW_RESIZE); // 左下
        handles[3].setCursor(Cursor.SE_RESIZE); // 右下

        setupMoveEvents();
    }

    // 设置裁剪框的移动逻辑
    private void setupMoveEvents() {
        cropRect.setOnMousePressed(e -> {
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
            e.consume();
        });

        cropRect.setOnMouseDragged(e -> {
            double deltaX = e.getSceneX() - mouseX;
            double deltaY = e.getSceneY() - mouseY;

            double newX = cropRect.getX() + deltaX;
            double newY = cropRect.getY() + deltaY;

            // 边界检查
            if (newX >= 0 && newX + cropRect.getWidth() <= container.getWidth()) cropRect.setX(newX);
            if (newY >= 0 && newY + cropRect.getHeight() <= container.getHeight()) cropRect.setY(newY);

            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
            refresh();
            e.consume();
        });
    }

    // 设置手柄的拉伸逻辑
    private void setupHandleEvent(Rectangle handle, int index) {
        handle.setOnMousePressed(e -> {
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
            e.consume();
        });

        handle.setOnMouseDragged(e -> {
            double deltaX = e.getSceneX() - mouseX;
            double deltaY = e.getSceneY() - mouseY;

            double x = cropRect.getX();
            double y = cropRect.getY();
            double w = cropRect.getWidth();
            double h = cropRect.getHeight();

            switch (index) {
                case 0: // 左上
                    if (w - deltaX > 20 && x + deltaX >= 0) { cropRect.setX(x + deltaX); cropRect.setWidth(w - deltaX); }
                    if (h - deltaY > 20 && y + deltaY >= 0) { cropRect.setY(y + deltaY); cropRect.setHeight(h - deltaY); }
                    break;
                case 1: // 右上
                    if (w + deltaX > 20 && x + w + deltaX <= container.getWidth()) { cropRect.setWidth(w + deltaX); }
                    if (h - deltaY > 20 && y + deltaY >= 0) { cropRect.setY(y + deltaY); cropRect.setHeight(h - deltaY); }
                    break;
                case 2: // 左下
                    if (w - deltaX > 20 && x + deltaX >= 0) { cropRect.setX(x + deltaX); cropRect.setWidth(w - deltaX); }
                    if (h + deltaY > 20 && y + h + deltaY <= container.getHeight()) { cropRect.setHeight(h + deltaY); }
                    break;
                case 3: // 右下
                    if (w + deltaX > 20 && x + w + deltaX <= container.getWidth()) { cropRect.setWidth(w + deltaX); }
                    if (h + deltaY > 20 && y + h + deltaY <= container.getHeight()) { cropRect.setHeight(h + deltaY); }
                    break;
            }
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
            refresh();
            e.consume();
        });
    }

    // 刷新遮罩和手柄位置
    private void refresh() {
        // 1. 刷新遮罩镂空效果
        maskPath.getElements().clear();
        // 外围矩形
        maskPath.getElements().add(new MoveTo(0, 0));
        maskPath.getElements().add(new LineTo(container.getWidth(), 0));
        maskPath.getElements().add(new LineTo(container.getWidth(), container.getHeight()));
        maskPath.getElements().add(new LineTo(0, container.getHeight()));
        maskPath.getElements().add(new ClosePath());
        // 内部镂空矩形
        maskPath.getElements().add(new MoveTo(cropRect.getX(), cropRect.getY()));
        maskPath.getElements().add(new LineTo(cropRect.getX() + cropRect.getWidth(), cropRect.getY()));
        maskPath.getElements().add(new LineTo(cropRect.getX() + cropRect.getWidth(), cropRect.getY() + cropRect.getHeight()));
        maskPath.getElements().add(new LineTo(cropRect.getX(), cropRect.getY() + cropRect.getHeight()));
        maskPath.getElements().add(new ClosePath());

        // 2. 刷新手柄位置
        handles[0].setX(cropRect.getX() - HANDLE_SIZE / 2); handles[0].setY(cropRect.getY() - HANDLE_SIZE / 2);
        handles[1].setX(cropRect.getX() + cropRect.getWidth() - HANDLE_SIZE / 2); handles[1].setY(cropRect.getY() - HANDLE_SIZE / 2);
        handles[2].setX(cropRect.getX() - HANDLE_SIZE / 2); handles[2].setY(cropRect.getY() + cropRect.getHeight() - HANDLE_SIZE / 2);
        handles[3].setX(cropRect.getX() + cropRect.getWidth() - HANDLE_SIZE / 2); handles[3].setY(cropRect.getY() + cropRect.getHeight() - HANDLE_SIZE / 2);
    }

    public void activate() {
        refresh();
        container.getChildren().setAll(maskPath, cropRect);
        container.getChildren().addAll(handles);
        container.setVisible(true);
        container.setMouseTransparent(false);
    }

    public void deactivate() {
        container.setVisible(false);
        container.getChildren().clear();
    }

    public javafx.scene.shape.Rectangle getSelection() {
        return cropRect;
    }
}
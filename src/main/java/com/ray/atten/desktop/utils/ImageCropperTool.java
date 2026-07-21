package com.ray.atten.desktop.utils;

import javafx.scene.Cursor;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

public class ImageCropperTool {
    private final Pane container;
    private final Path maskPath;      // 镂空遮罩层

    // 【核心修改】：將原本的一個大框，在物理上拆分為三個交互觸發區域
    private final Rectangle cropRect;       // 視覺上的大白框（只負責畫白線，不接收滑鼠事件）
    private final Rectangle leftResizeZone; // 隱形的左邊界拉伸區
    private final Rectangle rightResizeZone;// 隱形的右邊界拉伸區
    private final Rectangle centerDragZone; // 隱形的中間高亮拖拽區

    private double mouseX;
    private final double ZONE_WIDTH = 12.0; // 邊緣響應熱區寬度（像素）

    public ImageCropperTool(Pane container) {
        this.container = container;

        // 1. 初始化半透明遮罩
        maskPath = new Path();
        maskPath.setFill(Color.rgb(0, 0, 0, 0.6));
        maskPath.setStroke(null);
        maskPath.setFillRule(FillRule.EVEN_ODD);
        maskPath.setMouseTransparent(true); // 遮罩不擋滑鼠

        // 2. 初始化視覺白框（將其設置為不響應滑鼠，純裝飾）
        cropRect = new Rectangle(200, 0, 200, 200);
        cropRect.setStroke(Color.WHITE);
        cropRect.setStrokeWidth(2);
        cropRect.setFill(Color.TRANSPARENT);
        cropRect.setMouseTransparent(true);

        // 3. 初始化三個物理交互區（全部填充透明，靠物理邊界隔離事件）
        leftResizeZone = new Rectangle(ZONE_WIDTH, 200, Color.TRANSPARENT);
        leftResizeZone.setCursor(Cursor.H_RESIZE); // 永遠是左右拉伸指針 ↔

        rightResizeZone = new Rectangle(ZONE_WIDTH, 200, Color.TRANSPARENT);
        rightResizeZone.setCursor(Cursor.H_RESIZE); // 永遠是左右拉伸指针 ↔

        centerDragZone = new Rectangle(100, 200, Color.TRANSPARENT);
        centerDragZone.setCursor(Cursor.MOVE);     // 永遠是移動指針 ➕

        setupZoneEvents();
    }

    // 【核心修改】：為三個區域分別綁定各自純粹的事件，互不干擾
    private void setupZoneEvents() {
        // --- A. 左邊界拉伸邏輯 ---
        leftResizeZone.setOnMousePressed(e -> {
            mouseX = e.getSceneX();
            e.consume();
        });
        leftResizeZone.setOnMouseDragged(e -> {
            double deltaX = e.getSceneX() - mouseX;
            double newX = cropRect.getX() + deltaX;
            double newW = cropRect.getWidth() - deltaX;
            if (newX >= 0 && newW > 60) {
                cropRect.setX(newX);
                cropRect.setWidth(newW);
                refresh();
            }
            mouseX = e.getSceneX();
            e.consume();
        });

        // --- B. 右邊界拉伸邏輯 ---
        rightResizeZone.setOnMousePressed(e -> {
            mouseX = e.getSceneX();
            e.consume();
        });
        rightResizeZone.setOnMouseDragged(e -> {
            double deltaX = e.getSceneX() - mouseX;
            double newW = cropRect.getWidth() + deltaX;
            if (cropRect.getX() + newW <= container.getWidth() && newW > 60) {
                cropRect.setWidth(newW);
                refresh();
            }
            mouseX = e.getSceneX();
            e.consume();
        });

        // --- C. 中間高亮平移邏輯 ---
        centerDragZone.setOnMousePressed(e -> {
            mouseX = e.getSceneX();
            e.consume();
        });
        centerDragZone.setOnMouseDragged(e -> {
            double deltaX = e.getSceneX() - mouseX;
            double newX = cropRect.getX() + deltaX;
            if (newX >= 0 && newX + cropRect.getWidth() <= container.getWidth()) {
                cropRect.setX(newX);
                refresh();
            }
            mouseX = e.getSceneX();
            e.consume();
        });
    }

    // 【核心修改】：在刷新時，動態讓三個隱形交互區的尺寸、位置與大白框完美對齊綁定
    public void refresh() {
        double x = cropRect.getX();
        double w = cropRect.getWidth();
        double h = container.getHeight();

        // 強制大白框隨時頂天立地
        cropRect.setY(0);
        cropRect.setHeight(h);

        // 讓左交互區騎在左白線上（一半在內，一半在外）
        leftResizeZone.setX(x - ZONE_WIDTH / 2);
        leftResizeZone.setY(0);
        leftResizeZone.setHeight(h);

        // 讓右交互區騎在右白線上
        rightResizeZone.setX(x + w - ZONE_WIDTH / 2);
        rightResizeZone.setY(0);
        rightResizeZone.setHeight(h);

        // 中間拖拽區完美填滿左右邊界之間的空白高亮區
        centerDragZone.setX(x + ZONE_WIDTH / 2);
        centerDragZone.setY(0);
        centerDragZone.setWidth(w - ZONE_WIDTH);
        centerDragZone.setHeight(h);

        // 刷新遮罩鏤空
        maskPath.getElements().clear();
        maskPath.getElements().add(new MoveTo(0, 0));
        maskPath.getElements().add(new LineTo(container.getWidth(), 0));
        maskPath.getElements().add(new LineTo(container.getWidth(), container.getHeight()));
        maskPath.getElements().add(new LineTo(0, container.getHeight()));
        maskPath.getElements().add(new ClosePath());

        maskPath.getElements().add(new MoveTo(x, 0));
        maskPath.getElements().add(new LineTo(x + w, 0));
        maskPath.getElements().add(new LineTo(x + w, h));
        maskPath.getElements().add(new LineTo(x, h));
        maskPath.getElements().add(new ClosePath());
    }

    public void activate() {
        refresh();
        // 將遮罩、白框和三個隱形交互層一起放入容器
        container.getChildren().setAll(maskPath, cropRect, leftResizeZone, rightResizeZone, centerDragZone);
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
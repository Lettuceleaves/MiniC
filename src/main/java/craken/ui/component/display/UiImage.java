package craken.ui.component.display;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import craken.ui.component.UiComponent;

/** 保持比例并裁入自身布局区域的图片组件。 */
public final class UiImage extends StackPane implements UiComponent {
    private final ImageView imageView = new ImageView();

    public UiImage() {
        this(null);
    }

    public UiImage(Image image) {
        setMinSize(0, 0);
        getStyleClass().add("ui-image");
        imageView.setImage(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(widthProperty());
        imageView.fitHeightProperty().bind(heightProperty());
        getChildren().add(imageView);
        setPrefSize(160, 120);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }

    public Image image() {
        return imageView.getImage();
    }

    public void image(Image image) {
        imageView.setImage(image);
    }
}

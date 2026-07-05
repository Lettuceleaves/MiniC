package minic.runtime.debug.visual.layout;

/**
 * Allowed node edge anchors.
 */
public enum NodeAnchor {
    TOP {
        @Override
        public GridPoint point(GridRect rect) {
            return rect.top();
        }
    },
    RIGHT {
        @Override
        public GridPoint point(GridRect rect) {
            return rect.right();
        }
    },
    BOTTOM {
        @Override
        public GridPoint point(GridRect rect) {
            return rect.bottom();
        }
    },
    LEFT {
        @Override
        public GridPoint point(GridRect rect) {
            return rect.left();
        }
    };

    public abstract GridPoint point(GridRect rect);
}

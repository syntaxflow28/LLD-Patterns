package problems.elevator;

/** Travel direction. IDLE is a real value, not null — a Null Object for "not going anywhere". */
public enum Direction {

    UP("^"),
    DOWN("v"),
    IDLE("-");

    private final String symbol;

    Direction(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}

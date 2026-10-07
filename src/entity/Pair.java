package entity;

import java.io.Serializable;
import java.util.Objects;

public class Pair<L, R> implements Serializable {
    private final L key;
    private final R value;

    public Pair(L key, R value) {
        this.key = key;
        this.value = value;
    }

    public L getKey() {
        return key;
    }

    public R getValue() {
        return value;
    }

    public static <L, R> Pair<L, R> of(L key, R value) {
        return new Pair<>(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair<?, ?> pair)) return false;
        return Objects.equals(key, pair.key) && Objects.equals(value, pair.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "Pair{" + "key=" + key + ", value=" + value + '}';
    }
}

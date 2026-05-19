struct Inner {
    int value;
    int extra;
};

struct Outer {
    struct Inner inner;
    int flag;
};

int getInnerValue(struct Outer o) {
    return o.inner.value;
}

int main() {
    struct Outer outer;
    outer.inner.value = 42;
    outer.inner.extra = 10;
    outer.flag = 1;

    struct Outer copy;
    copy = outer;

    int result = copy.inner.value + copy.inner.extra + copy.flag;
    return result;
}

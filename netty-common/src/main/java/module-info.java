import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.netty.common {

    requires static io.netty.transport.epoll;
    requires static io.netty.transport.classes.epoll;
    requires static io.netty.transport.kqueue;
    requires static io.netty.transport.classes.kqueue;
    requires static io.netty.transport.io_uring;
    requires static io.netty.transport.classes.io_uring;

    requires transitive io.netty.common;
    requires transitive io.netty.transport;
    requires transitive io.netty.transport.unix.common;

    requires kora.common;
    requires kora.config.common;

    exports io.koraframework.netty.common;
}

package k;

import O0.U3;
import java.util.concurrent.Executors;

/* renamed from: k.a  reason: case insensitive filesystem */
/* loaded from: classes.dex */
public final class C0637a extends U3 {

    /* renamed from: b  reason: collision with root package name */
    public static volatile C0637a f5845b;

    /* renamed from: a  reason: collision with root package name */
    public final Object f5846a;

    public C0637a(int i3) {
        switch (i3) {
            case 1:
                this.f5846a = new Object();
                Executors.newFixedThreadPool(4, new ThreadFactoryC0638b());
                return;
            default:
                this.f5846a = new C0637a(1);
                return;
        }
    }

    public static C0637a a() {
        if (f5845b != null) {
            return f5845b;
        }
        synchronized (C0637a.class) {
            try {
                if (f5845b == null) {
                    f5845b = new C0637a(0);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return f5845b;
    }
}
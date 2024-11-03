package l2;

/* JADX WARN: Failed to restore enum class, 'enum' modifier and super class removed */
/* JADX WARN: Unknown enum class pattern. Please report as an issue! */
/* renamed from: l2.a  reason: case insensitive filesystem */
/* loaded from: classes.dex */
public final class EnumC0669a {

    /* renamed from: b  reason: collision with root package name */
    public static final EnumC0669a f6022b;

    /* renamed from: c  reason: collision with root package name */
    public static final /* synthetic */ EnumC0669a[] f6023c;

    /* JADX WARN: Type inference failed for: r0v0, types: [java.lang.Enum, l2.a] */
    /* JADX WARN: Type inference failed for: r1v1, types: [java.lang.Enum, l2.a] */
    /* JADX WARN: Type inference failed for: r2v2, types: [java.lang.Enum, l2.a] */
    static {
        ?? r02 = new Enum("COROUTINE_SUSPENDED", 0);
        f6022b = r02;
        f6023c = new EnumC0669a[]{r02, new Enum("UNDECIDED", 1), new Enum("RESUMED", 2)};
    }

    public static EnumC0669a valueOf(String str) {
        return (EnumC0669a) Enum.valueOf(EnumC0669a.class, str);
    }

    public static EnumC0669a[] values() {
        return (EnumC0669a[]) f6023c.clone();
    }
}
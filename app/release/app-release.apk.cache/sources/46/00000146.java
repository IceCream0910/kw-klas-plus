package H1;

import C0.w;

/* loaded from: classes.dex */
public final class a extends Exception {

    /* renamed from: b  reason: collision with root package name */
    public final int f556b;

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public a(String str, int i3) {
        super(str);
        w.c(str, "Provided message must not be empty.");
        this.f556b = i3;
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public a(Exception exc) {
        super("Internal error has occurred when executing ML Kit tasks", exc);
        w.c("Internal error has occurred when executing ML Kit tasks", "Provided message must not be empty.");
        this.f556b = 13;
    }
}
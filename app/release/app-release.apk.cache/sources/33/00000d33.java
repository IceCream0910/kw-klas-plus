package m1;

import android.view.View;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.activity.q;
import java.util.Objects;

/* loaded from: classes.dex */
public class c {

    /* renamed from: a  reason: collision with root package name */
    public OnBackInvokedCallback f6096a;

    public OnBackInvokedCallback a(b bVar) {
        Objects.requireNonNull(bVar);
        return new q(3, bVar);
    }

    /* JADX WARN: Code restructure failed: missing block: B:5:0x0005, code lost:
        r3 = r3.findOnBackInvokedDispatcher();
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public void b(m1.b r2, android.view.View r3, boolean r4) {
        /*
            r1 = this;
            android.window.OnBackInvokedCallback r0 = r1.f6096a
            if (r0 == 0) goto L5
            return
        L5:
            android.window.OnBackInvokedDispatcher r3 = K.g.i(r3)
            if (r3 != 0) goto Lc
            return
        Lc:
            android.window.OnBackInvokedCallback r2 = r1.a(r2)
            r1.f6096a = r2
            if (r4 == 0) goto L18
            r1 = 1000000(0xf4240, float:1.401298E-39)
            goto L19
        L18:
            r1 = 0
        L19:
            K.g.n(r3, r1, r2)
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: m1.c.b(m1.b, android.view.View, boolean):void");
    }

    public void c(View view) {
        OnBackInvokedDispatcher findOnBackInvokedDispatcher;
        findOnBackInvokedDispatcher = view.findOnBackInvokedDispatcher();
        if (findOnBackInvokedDispatcher == null) {
            return;
        }
        findOnBackInvokedDispatcher.unregisterOnBackInvokedCallback(this.f6096a);
        this.f6096a = null;
    }
}
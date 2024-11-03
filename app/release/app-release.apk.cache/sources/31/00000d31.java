package m1;

import O0.Z3;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.view.View;
import com.icecream.kwklasplus.R;

/* renamed from: m1.a  reason: case insensitive filesystem */
/* loaded from: classes.dex */
public abstract class AbstractC0676a {

    /* renamed from: a  reason: collision with root package name */
    public final TimeInterpolator f6091a;

    /* renamed from: b  reason: collision with root package name */
    public final View f6092b;

    /* renamed from: c  reason: collision with root package name */
    public final int f6093c;

    /* renamed from: d  reason: collision with root package name */
    public final int f6094d;

    /* renamed from: e  reason: collision with root package name */
    public final int f6095e;
    public androidx.activity.b f;

    public AbstractC0676a(View view) {
        this.f6092b = view;
        Context context = view.getContext();
        this.f6091a = Z3.d(context, R.attr.motionEasingStandardDecelerateInterpolator, L.a.b(0.0f, 0.0f, 0.0f, 1.0f));
        this.f6093c = Z3.c(context, R.attr.motionDurationMedium2, 300);
        this.f6094d = Z3.c(context, R.attr.motionDurationShort3, 150);
        this.f6095e = Z3.c(context, R.attr.motionDurationShort2, 100);
    }
}
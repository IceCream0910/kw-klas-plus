package Y0;

import A0.m;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Locale;

/* loaded from: classes.dex */
public final class b implements Parcelable {
    public static final Parcelable.Creator<b> CREATOR = new m(18);

    /* renamed from: A  reason: collision with root package name */
    public Integer f2347A;

    /* renamed from: B  reason: collision with root package name */
    public Integer f2348B;

    /* renamed from: C  reason: collision with root package name */
    public Integer f2349C;

    /* renamed from: D  reason: collision with root package name */
    public Integer f2350D;
    public Boolean E;

    /* renamed from: b  reason: collision with root package name */
    public int f2351b;

    /* renamed from: c  reason: collision with root package name */
    public Integer f2352c;

    /* renamed from: d  reason: collision with root package name */
    public Integer f2353d;

    /* renamed from: e  reason: collision with root package name */
    public Integer f2354e;
    public Integer f;
    public Integer g;

    /* renamed from: h  reason: collision with root package name */
    public Integer f2355h;

    /* renamed from: i  reason: collision with root package name */
    public Integer f2356i;

    /* renamed from: j  reason: collision with root package name */
    public int f2357j;

    /* renamed from: k  reason: collision with root package name */
    public String f2358k;

    /* renamed from: l  reason: collision with root package name */
    public int f2359l;
    public int m;

    /* renamed from: n  reason: collision with root package name */
    public int f2360n;

    /* renamed from: o  reason: collision with root package name */
    public Locale f2361o;

    /* renamed from: p  reason: collision with root package name */
    public CharSequence f2362p;

    /* renamed from: q  reason: collision with root package name */
    public CharSequence f2363q;

    /* renamed from: r  reason: collision with root package name */
    public int f2364r;

    /* renamed from: s  reason: collision with root package name */
    public int f2365s;

    /* renamed from: t  reason: collision with root package name */
    public Integer f2366t;

    /* renamed from: u  reason: collision with root package name */
    public Boolean f2367u;

    /* renamed from: v  reason: collision with root package name */
    public Integer f2368v;

    /* renamed from: w  reason: collision with root package name */
    public Integer f2369w;

    /* renamed from: x  reason: collision with root package name */
    public Integer f2370x;

    /* renamed from: y  reason: collision with root package name */
    public Integer f2371y;

    /* renamed from: z  reason: collision with root package name */
    public Integer f2372z;

    @Override // android.os.Parcelable
    public final int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i3) {
        parcel.writeInt(this.f2351b);
        parcel.writeSerializable(this.f2352c);
        parcel.writeSerializable(this.f2353d);
        parcel.writeSerializable(this.f2354e);
        parcel.writeSerializable(this.f);
        parcel.writeSerializable(this.g);
        parcel.writeSerializable(this.f2355h);
        parcel.writeSerializable(this.f2356i);
        parcel.writeInt(this.f2357j);
        parcel.writeString(this.f2358k);
        parcel.writeInt(this.f2359l);
        parcel.writeInt(this.m);
        parcel.writeInt(this.f2360n);
        CharSequence charSequence = this.f2362p;
        parcel.writeString(charSequence != null ? charSequence.toString() : null);
        CharSequence charSequence2 = this.f2363q;
        parcel.writeString(charSequence2 != null ? charSequence2.toString() : null);
        parcel.writeInt(this.f2364r);
        parcel.writeSerializable(this.f2366t);
        parcel.writeSerializable(this.f2368v);
        parcel.writeSerializable(this.f2369w);
        parcel.writeSerializable(this.f2370x);
        parcel.writeSerializable(this.f2371y);
        parcel.writeSerializable(this.f2372z);
        parcel.writeSerializable(this.f2347A);
        parcel.writeSerializable(this.f2350D);
        parcel.writeSerializable(this.f2348B);
        parcel.writeSerializable(this.f2349C);
        parcel.writeSerializable(this.f2367u);
        parcel.writeSerializable(this.f2361o);
        parcel.writeSerializable(this.E);
    }
}
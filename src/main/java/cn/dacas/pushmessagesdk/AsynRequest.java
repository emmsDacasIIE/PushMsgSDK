package cn.dacas.pushmessagesdk;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.util.Map;

/**
 * Created by Sun RX on 2016-9-13.
 */
public abstract class AsynRequest<T> extends Request<T>{

    protected int mMethod;// get post delete
    protected String mUrl;
    protected Map<String, String> mHeaderMap;
    protected Response.Listener<T> mListener;
    protected Response.ErrorListener mErrorListener;
    protected int codeType;

    public AsynRequest(int method, String url, int type,  Map<String, String> map, Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        mMethod=method;
        mUrl=url;
        codeType=type;
        mHeaderMap = map;
        mListener = listener;
        mErrorListener=errorListener;
    }

    @Override
    protected Map<String, String> getParams() throws AuthFailureError {
        return mHeaderMap;
    }

    @Override
    protected void deliverResponse(T response) {
        if (mListener!=null)
            mListener.onResponse(response);
    }
}

package cn.dacas.pushmessagesdk;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Created by Sun RX on 2016-9-13.
 */
public class MyStringRequest extends AsynRequest<String> {

    public MyStringRequest(int method,
                           String url,
                           int type,
                           Map<String, String> map,
                           Response.Listener<String> listener,
                           Response.ErrorListener errorListener) {
        super(method, url, type, map, listener, errorListener);
    }

    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        String str = null;
        try {
            switch (codeType){
                //Add Aliases status == 200
                case PushMsgManager.CommCodeType.NET_AddAliases:
                    str = (response.statusCode == 200)? "Ok":"Err";
                    break;
                //Add status == 201 Created
                case PushMsgManager.CommCodeType.NET_Add:
                    str = (response.statusCode == 200)? "Ok":"Err";
                    break;
                //Deleted status == 204 No Content
                case PushMsgManager.CommCodeType.NET_Delete:
                    str = (response.statusCode == 204)? "Ok":"Err";
                    break;
                //Other Cases with response
                default:
                    str = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
            }
            return Response.success(str,HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        }
    }
}

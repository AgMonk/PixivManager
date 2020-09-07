/**
 * 把对象序列化为 &key1=value1&key2=value2格式
 * @param data
 * @returns {string}
 */
queryString = function (data) {
    let s = "";
    for (let key in data) {
        if (typeof (data[key]) == "object") {
            let a = data[key];
            for (let k in a) {
                s += "&" + key + "=" + encodeURIComponent(a[k]);
            }
        } else {
            s += "&" + key + "=" + encodeURIComponent(data[key]);
        }
    }
    return s;
}

qs = queryString;


get = function (url, params, thenFunction) {
    url += url.includes("?") ? "" : "?";
    axios.get(url + qs(params)).then(thenFunction).catch(catchFunction)

}
post = function (url, params, thenFunction) {
    url += url.includes("?") ? "" : "?";
    axios.post(url + qs(params)).then(thenFunction).catch(catchFunction)
}


catchFunction = function (e) {
    console.log(e)
}
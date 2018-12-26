from flask import Flask, request
import requests
import json

pythonServiceHostName = "http://<ip>";

# Queue of backend servers in Azure and GCP
q = ['52.186.86.238','gcp-server']
# Count non-200 responses for each backend
status = {'52.186.86.238':0, 'gcp-server':0}

app = Flask(__name__, static_folder='site', static_url_path='')

@app.route("/", methods=['GET'])
def handle():
    return app.send_static_file("index.html")

@app.route("/python", methods=['GET', 'POST'])
def handlePython():
    if request.method == 'POST':

        if request.headers['Content-Type'] == 'application/json':
            code = request.get_json(force=True)['code']
        else:
            code = request.form['code']

        payload = {'code': code}
        headers = {'Content-Type': 'application/json',}

        # if receive more than 10 non-200 responses from a backend, stop sending requests to it
        if status[q[0]] > 10:
            service = q.pop(0)
            q.append(service)
            status[service] = 0
        dns = q.pop(0)
        url = "http://" + dns + ":80/py/eval"
        # Get result from backend
        r = requests.post(url,data=json.dumps(payload), headers=headers)
        if r.status_code != 200:
            status[dns] = status[dns] + 1
        q.append(dns)
        return r.text
        return ''
    else:
        return app.send_static_file("python.html")

if __name__ == '__main__':
    app.run(debug=True, host="0.0.0.0", port=5000, threaded=True)

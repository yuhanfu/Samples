from flask import Flask, request, Response
import tempfile
import os
import sys
import subprocess
import time
import json

app = Flask(__name__, static_url_path='')

@app.route("/py/eval", methods=['GET', 'POST'])
def handle():
    if request.method == 'POST':

        data = request.get_json(force=True)['code']
        # Create a temporary python file
        temp = tempfile.mkdtemp()
        filename = int(time.time()*1000)
        fpath = os.path.join(temp, '%s.py'%filename)
        # Write the code data to this file
        with open(fpath, 'w') as f:
            f.write(data)
        EXEC = sys.executable
        output = {}
        # Execute the python file and return the result
        try:
            outdata = subprocess.Popen([EXEC, fpath], stdin=subprocess.PIPE, stdout= subprocess.PIPE, stderr=subprocess.PIPE, shell=False)
            stdout,stderr = outdata.communicate()
            output['stdout'] = stdout
            output['stderr'] = stderr
        except:
            output['stderr'] = "error"
            output['stdout'] = "error"
        return Response(json.dumps(output), mimetype='application/json')

if __name__ == '__main__':
    app.run(threaded=True, debug=True, host="0.0.0.0", port=6000)

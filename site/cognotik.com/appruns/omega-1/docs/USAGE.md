Usage can be tracked by visiting a url on the site using the session id, for example:
http://localhost:12891/proxy/usage?sessionId=U-20260329-oae7

This will respond with an HTML page with a table by default.

## JSON Format

You can also request the usage data in JSON format by either:
1. Adding a `format=json` query parameter, e.g.:
    `http://localhost:12891/proxy/usage?sessionId=U-20260329-oae7&format=json`
2. Setting the `Accept: application/json` header in your request.

Example JSON response:  
```json
{
   "models": [
     {
       "model": "claude-haiku-4-5-20251001",
       "prompt_tokens": 4550,
       "completion_tokens": 4831,
       "cost": 0.0287
     }
   ],
   "totals": {
     "prompt_tokens": 4550,
     "completion_tokens": 4831,
     "cost": 0.0287
   }
}
```

## HTML Format

The default HTML response looks like this:
```html
<html>
<head>
    <title>Usage</title>
    <link rel="icon" type="image/svg+xml" href="/favicon.svg">
    <style>
        body { font-family: Arial, sans-serif; }
        table { width: 100%; border-collapse: collapse; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        tr:nth-child(even) { background-color: #f2f2f2; }
    </style>
</head>
<body>
<table class="usage-table">
    <tbody>
    <tr class="table-header">
        <th>Model</th>
        <th>Prompt</th>
        <th>Completion</th>
        <th>Cost</th>
    </tr>
    <tr class="table-row">
        <td class="model-cell">claude-haiku-4-5-20251001</td>
        <td class="prompt-cell">4550</td>
        <td class="completion-cell">4831</td>
        <td class="cost-cell">0.0287</td>
    </tr>
    <tr class="table-row">
        <td class="model-cell">Total</td>
        <td class="prompt-cell">4550</td>
        <td class="completion-cell">4831</td>
        <td class="cost-cell">0.0287</td>
    </tr>
    </tbody>
</table>

</body>
</html>
```
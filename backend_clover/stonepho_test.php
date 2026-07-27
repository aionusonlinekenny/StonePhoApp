<?php
header('Content-Type: text/plain');
echo 'PHP OK - version: ' . phpversion();
echo ' | cURL: ' . (function_exists('curl_init') ? 'YES' : 'NO');
echo ' | json: ' . (function_exists('json_encode') ? 'YES' : 'NO');

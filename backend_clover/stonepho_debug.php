<?php
header('Content-Type: text/plain');

echo "1. START\n";

ini_set('display_errors', 1);
error_reporting(E_ALL);

echo "2. ini_set OK\n";

define('MID2',   'GW3XFCV71AK81');
define('TOKEN2', 'c30698f2-347e-add6-b758-44285d0e6cac');
define('BASE2',  'https://api.clover.com/v3/merchants/' . MID2);

echo "3. define OK | BASE2=" . BASE2 . "\n";

$ch = curl_init('https://api.clover.com/v3/merchants/' . MID2);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_HTTPHEADER, array(
    'Authorization: Bearer ' . TOKEN2,
    'Accept: application/json'
));
curl_setopt($ch, CURLOPT_TIMEOUT, 15);
curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 8);

echo "4. curl init OK\n";

$body = curl_exec($ch);
$code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
$err  = curl_error($ch);
curl_close($ch);

echo "5. curl exec done | HTTP=" . $code . " | err=" . $err . "\n";
echo "6. body (first 200 chars): " . substr(($body ? $body : '(empty)'), 0, 200) . "\n";

$data = json_decode($body, true);
echo "7. JSON decode OK | merchant_name=" . (isset($data['name']) ? $data['name'] : 'N/A') . "\n";

echo "8. DONE\n";

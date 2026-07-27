<?php
ini_set('display_errors', 0);
ini_set('memory_limit', '128M');
set_time_limit(45);
error_reporting(0);

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');

define('API_KEY', 'StonePhoClover@2024');
define('MID',    'GW3XFCV71AK81');
define('TOKEN',  'c30698f2-347e-add6-b758-44285d0e6cac');
define('BASE',   'https://api.clover.com/v3/merchants/' . MID);

function get_sent_key() {
    $h = isset($_SERVER['HTTP_AUTHORIZATION'])          ? $_SERVER['HTTP_AUTHORIZATION']          : '';
    if (!$h && isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
        $h = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
    }
    if (!$h && function_exists('apache_request_headers')) {
        $all = apache_request_headers();
        if (isset($all['Authorization']))  $h = $all['Authorization'];
        elseif (isset($all['authorization'])) $h = $all['authorization'];
    }
    $fromHeader = trim(str_replace('Bearer ', '', $h));
    return $fromHeader ? $fromHeader : (isset($_GET['key']) ? $_GET['key'] : '');
}

if (get_sent_key() !== API_KEY) {
    http_response_code(401);
    die(json_encode(array('error' => 'Unauthorized')));
}

if (!function_exists('curl_init')) {
    http_response_code(503);
    die(json_encode(array('error' => 'cURL not available')));
}

function clover_call($url) {
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, array(
        'Authorization: Bearer ' . TOKEN,
        'Accept: application/json'
    ));
    curl_setopt($ch, CURLOPT_TIMEOUT, 20);
    curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 10);
    $body = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err  = curl_error($ch);
    curl_close($ch);
    return array(
        'code' => $code,
        'body' => ($body === false) ? '' : $body,
        'err'  => $err
    );
}

function clover($path) {
    $r = clover_call(BASE . $path);
    if ($r['err']) {
        http_response_code(502);
        die(json_encode(array('error' => 'curl: ' . $r['err'])));
    }
    if ($r['code'] < 200 || $r['code'] >= 300) {
        http_response_code(502);
        die(json_encode(array('error' => 'Clover HTTP ' . $r['code'], 'detail' => substr($r['body'], 0, 300))));
    }
    return $r['body'];
}

$action = isset($_GET['action']) ? $_GET['action'] : 'orders';

switch ($action) {

    case 'ping':
        $r = clover_call('https://api.clover.com/v3/merchants/' . MID);
        $merchant = json_decode($r['body'], true);
        echo json_encode(array(
            'ok'            => true,
            'server'        => 'stonepho_clover.php',
            'mid'           => MID,
            'token_tail'    => '...' . substr(TOKEN, -6),
            'time'          => date('Y-m-d H:i:s T'),
            'clover_http'   => $r['code'],
            'merchant_name' => isset($merchant['name']) ? $merchant['name'] : null,
            'clover_err'    => $r['err'] ? $r['err'] : null,
            'php_version'   => phpversion(),
        ));
        break;

    case 'orders':
        $r = clover_call(
            'https://api.clover.com/v3/merchants/' . MID
            . '/atomic_order/orders?limit=50'
            . '&expand=lineItems%2ClineItems.item%2CorderType'
        );
        if ($r['body'] !== '' && $r['code'] >= 200 && $r['code'] < 300) {
            echo $r['body'];
        } else {
            echo clover(
                '/orders?orderBy=createdTime+DESC'
                . '&expand=lineItems%2ClineItems.item%2CorderType'
                . '&limit=50'
            );
        }
        break;

    case 'rest_orders':
        echo clover(
            '/orders?orderBy=createdTime+DESC'
            . '&expand=lineItems%2ClineItems.item%2CorderType'
            . '&limit=30'
        );
        break;

    case 'debug':
        echo clover('/orders?orderBy=createdTime+DESC&limit=20');
        break;

    case 'states':
        $raw  = json_decode(clover('/orders?orderBy=createdTime+DESC&limit=30'), true);
        $list = array();
        foreach (isset($raw['elements']) ? $raw['elements'] : array() as $o) {
            $list[] = array(
                'id'           => isset($o['id'])           ? $o['id']           : '',
                'title'        => isset($o['title'])        ? $o['title']        : '',
                'state'        => isset($o['state'])        ? $o['state']        : '',
                'paymentState' => isset($o['paymentState']) ? $o['paymentState'] : '',
                'total'        => isset($o['total'])        ? $o['total']        : 0,
            );
        }
        echo json_encode(array('count' => count($list), 'orders' => $list));
        break;

    case 'atomic':
        $r = clover_call('https://api.clover.com/v3/merchants/' . MID . '/atomic_order/orders?limit=20');
        echo ($r['body'] !== '') ? $r['body'] : json_encode(array('error' => $r['err'], 'code' => $r['code']));
        break;

    case 'tables':
        echo clover('/tables?limit=200');
        break;

    default:
        http_response_code(404);
        echo json_encode(array('error' => 'Unknown action: ' . $action));
}

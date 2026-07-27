<?php
/**
 * StonePhoApp – Clover standalone proxy
 *
 * Upload file này lên server tại BẤT KỲ đường dẫn nào, ví dụ:
 *   /loyalteapp/backend/stonepho_clover.php
 *
 * Truy cập test:
 *   https://www.stonephovaldosta.com/loyalteapp/backend/stonepho_clover.php?action=ping&key=StonePhoClover@2024
 *   https://www.stonephovaldosta.com/loyalteapp/backend/stonepho_clover.php?action=orders&key=StonePhoClover@2024
 */

// ── Bắt lỗi PHP fatal trước khi bất kỳ output nào ───────────────────────────
ini_set('display_errors', '0');
ini_set('memory_limit', '256M');
set_time_limit(60);

register_shutdown_function(function () {
    $e = error_get_last();
    if ($e && in_array($e['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR])) {
        if (!headers_sent()) {
            http_response_code(500);
            header('Content-Type: application/json; charset=utf-8');
        }
        echo json_encode([
            'error' => 'PHP fatal: ' . $e['message'],
            'file'  => basename($e['file']),
            'line'  => $e['line'],
        ]);
    }
});

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');

// ── Kiểm tra cURL ─────────────────────────────────────────────────────────────
if (!function_exists('curl_init')) {
    http_response_code(503);
    exit(json_encode(['error' => 'cURL extension not available on server']));
}

// ── Auth ──────────────────────────────────────────────────────────────────────
define('API_KEY', 'StonePhoClover@2024');

function get_sent_key(): string {
    $h = $_SERVER['HTTP_AUTHORIZATION']
      ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION']
      ?? '';
    if (!$h && function_exists('apache_request_headers')) {
        $all = apache_request_headers();
        $h   = $all['Authorization'] ?? $all['authorization'] ?? '';
    }
    $fromHeader = trim(str_replace('Bearer ', '', $h));
    return $fromHeader ?: ($_GET['key'] ?? '');
}

if (get_sent_key() !== API_KEY) {
    http_response_code(401);
    exit(json_encode(['error' => 'Unauthorized']));
}

// ── Clover credentials ────────────────────────────────────────────────────────
define('MID',   'GW3XFCV71AK81');
define('TOKEN', 'c30698f2-347e-add6-b758-44285d0e6cac');
define('BASE',  'https://api.clover.com/v3/merchants/' . MID);

function clover(string $path): string {
    $ch = curl_init(BASE . $path);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => ['Authorization: Bearer ' . TOKEN, 'Accept: application/json'],
        CURLOPT_TIMEOUT        => 20,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_SSL_VERIFYPEER => true,
    ]);
    $body = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err  = curl_error($ch);
    curl_close($ch);

    if ($err || $body === false) {
        http_response_code(502);
        exit(json_encode(['error' => 'cURL: ' . ($err ?: 'no response')]));
    }
    if ($code < 200 || $code >= 300) {
        http_response_code(502);
        exit(json_encode(['error' => "Clover HTTP $code", 'body' => substr($body, 0, 500)]));
    }
    return $body;
}

function clover_raw(string $path): array {
    $ch = curl_init('https://api.clover.com' . $path);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => ['Authorization: Bearer ' . TOKEN, 'Accept: application/json'],
        CURLOPT_TIMEOUT        => 20,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_SSL_VERIFYPEER => true,
    ]);
    $body = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err  = curl_error($ch);
    curl_close($ch);
    return [
        'code' => $code,
        'body' => ($body !== false) ? $body : '',
        'err'  => $err,
    ];
}

// ── Action routing ────────────────────────────────────────────────────────────
$action = $_GET['action'] ?? 'orders';

try {

    switch ($action) {

        case 'ping':
            $r = clover_raw('/v3/merchants/' . MID);
            $merchant = json_decode($r['body'], true);
            echo json_encode([
                'ok'            => true,
                'server'        => 'stonepho_clover.php',
                'mid'           => MID,
                'token'         => '...' . substr(TOKEN, -6),
                'time'          => date('Y-m-d H:i:s T'),
                'clover_http'   => $r['code'],
                'merchant_name' => $merchant['name'] ?? null,
                'clover_err'    => $r['err'] ?: null,
                'php_version'   => PHP_VERSION,
                'curl_version'  => curl_version()['version'] ?? 'n/a',
            ]);
            break;

        case 'orders':
            // Thử atomic_order trước (chỉ trả orders chưa thanh toán trong Clover Dining)
            $atomic = clover_raw(
                '/v3/merchants/' . MID
                . '/atomic_order/orders?limit=50'
                . '&expand=lineItems%2ClineItems.item%2CorderType'
            );
            if ($atomic['body'] !== '' && $atomic['code'] >= 200 && $atomic['code'] < 300) {
                echo $atomic['body'];
            } else {
                // Fallback: REST orders
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
            $raw   = json_decode(clover('/orders?orderBy=createdTime+DESC&limit=30'), true);
            $items = array_map(fn($o) => [
                'id'           => $o['id'] ?? '',
                'title'        => $o['title'] ?? '',
                'state'        => $o['state'] ?? '',
                'paymentState' => $o['paymentState'] ?? '',
                'total'        => $o['total'] ?? 0,
            ], $raw['elements'] ?? []);
            echo json_encode(['count' => count($items), 'orders' => $items]);
            break;

        case 'atomic':
            $r = clover_raw('/v3/merchants/' . MID . '/atomic_order/orders?limit=20');
            echo ($r['body'] !== '') ? $r['body'] : json_encode(['error' => $r['err'], 'code' => $r['code']]);
            break;

        case 'tables':
            echo clover('/tables?limit=200');
            break;

        default:
            http_response_code(404);
            echo json_encode(['error' => "Unknown action: $action"]);
    }

} catch (Throwable $t) {
    http_response_code(500);
    echo json_encode([
        'error'   => $t->getMessage(),
        'type'    => get_class($t),
        'file'    => basename($t->getFile()),
        'line'    => $t->getLine(),
    ]);
}

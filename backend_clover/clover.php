<?php
/**
 * StonePhoApp – Clover proxy endpoint
 * Upload vào: /loyalteapp/backend/controllers/clover.php
 * Thêm vào index.php:
 *   case 'clover': require __DIR__ . '/controllers/clover.php'; break;
 */

header('Content-Type: application/json');

// ── Xác thực StonePhoApp secret key ──────────────────────────────────────────
define('STONEPHO_API_KEY', 'StonePhoClover@2024');

// Apache đôi khi strip Authorization header — thử nhiều cách
function get_auth_key(): string {
    // Cách 1: $_SERVER trực tiếp
    $h = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    // Cách 2: REDIRECT_ prefix (khi dùng mod_rewrite)
    if (!$h) $h = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    // Cách 3: apache_request_headers() nếu có
    if (!$h && function_exists('apache_request_headers')) {
        $hdrs = apache_request_headers();
        $h = $hdrs['Authorization'] ?? $hdrs['authorization'] ?? '';
    }
    return trim(str_replace('Bearer ', '', $h));
}

$sentKey = get_auth_key();
// Cũng cho phép truyền qua query param ?key=... để test từ browser
if (!$sentKey) $sentKey = $_GET['key'] ?? '';

if ($sentKey !== STONEPHO_API_KEY) {
    http_response_code(401);
    exit(json_encode(['error' => 'Unauthorized', 'hint' => 'Send: Authorization: Bearer ' . STONEPHO_API_KEY]));
}

// ── Clover credentials (fallback hardcoded nếu config.php không define) ──────
if (file_exists(__DIR__ . '/../config.php')) {
    require_once __DIR__ . '/../config.php';
}
if (!defined('CLOVER_MERCHANT_ID')) define('CLOVER_MERCHANT_ID', '04VMDMMGF5K81');
if (!defined('CLOVER_API_TOKEN'))   define('CLOVER_API_TOKEN',   '10eeb58d-f5be-e989-2f6d-53363561948a');

define('CLOVER_API_BASE', 'https://api.clover.com/v3/merchants/' . CLOVER_MERCHANT_ID);

// ── Helper: gọi Clover API ───────────────────────────────────────────────────
function clover_get(string $path): string {
    $url = CLOVER_API_BASE . $path;
    $ch  = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => [
            'Authorization: Bearer ' . CLOVER_API_TOKEN,
            'Accept: application/json',
        ],
        CURLOPT_TIMEOUT        => 15,
        CURLOPT_SSL_VERIFYPEER => true,
    ]);
    $body = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err  = curl_error($ch);
    curl_close($ch);

    if ($err) {
        http_response_code(502);
        exit(json_encode(['error' => 'cURL error', 'detail' => $err]));
    }
    if ($code < 200 || $code >= 300) {
        http_response_code(502);
        exit(json_encode(['error' => "Clover API HTTP $code", 'detail' => $body]));
    }
    return $body;
}

// ── Routing ──────────────────────────────────────────────────────────────────
// Lấy path segment sau /api/clover/ theo nhiều cách routing khác nhau
$method  = $_SERVER['REQUEST_METHOD'];
$uri     = parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_PATH);
$uriParts = array_filter(explode('/', $uri));
// Lấy segment cuối (vd: /api/clover/orders → 'orders')
$lastSeg = end($uriParts) ?: '';

// Cũng hỗ trợ biến $sub và $id từ index.php routing nếu có
$segment = $sub ?? $id ?? $lastSeg;

switch ($method) {

    case 'GET':
        switch ($segment) {

            // GET /api/clover/orders — order đang mở, kèm line items + table label
            case 'orders':
            case '':
                $raw  = clover_get(
                    '/orders?filter=state%3Dopen'
                    . '&expand=lineItems%2ClineItems.item%2CorderType'
                    . '&limit=100'
                );
                echo $raw;
                break;

            // GET /api/clover/tables — danh sách bàn
            case 'tables':
                echo clover_get('/tables?limit=200');
                break;

            // GET /api/clover/ping — kiểm tra kết nối
            case 'ping':
                echo json_encode([
                    'ok'         => true,
                    'merchant'   => CLOVER_MERCHANT_ID,
                    'token_tail' => '...' . substr(CLOVER_API_TOKEN, -6),
                    'time'       => date('Y-m-d H:i:s'),
                ]);
                break;

            default:
                http_response_code(404);
                echo json_encode(['error' => "Not found: $segment"]);
        }
        break;

    // POST /api/clover/webhook — nhận event từ Clover
    case 'POST':
        if ($segment === 'webhook') {
            $payload = json_decode(file_get_contents('php://input'), true) ?? [];
            // Clover gửi: {"type":"CREATE","appId":"...","merchants":{"elements":[{"id":"MID"}]}}
            // Thêm xử lý log ở đây nếu cần
            http_response_code(200);
            echo json_encode(['ok' => true]);
        } else {
            http_response_code(405);
            echo json_encode(['error' => 'Method not allowed']);
        }
        break;

    default:
        http_response_code(405);
        echo json_encode(['error' => 'Method not allowed']);
}

export default {
  async fetch(request, env) {
    const policy = await env.POLICY_KV.get('policy');
    return new Response(policy, {
      headers: { 'Content-Type': 'text/plain', 'Cache-Control': 'max-age=3600' }
    });
  }
}

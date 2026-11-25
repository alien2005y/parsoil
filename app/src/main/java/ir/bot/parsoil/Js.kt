package ir.bot.parsoil

object Js {
    private const val HELPERS = """
      var norm=function(s){return (s||'').replace(/[*:\s‌]/g,'')};
      var vis=function(e){return e&&e.offsetParent!==null};
      var byLabel=function(t){
        var els=document.querySelectorAll('label,span,div,td,th,p,b,strong');
        for(var i=0;i<els.length;i++){var e=els[i];
          if(e.children.length>2)continue;
          if(norm(e.textContent)!==norm(t))continue;
          var c=e.htmlFor&&document.getElementById(e.htmlFor);
          if(!c){var p=e;for(var k=0;k<4&&p&&!c;k++){p=p.parentElement;
            if(p)c=p.querySelector('input:not([type=hidden]):not([type=radio]):not([type=file]),select,textarea');}}
          if(c)return c;}
        return null};
    """
    // فرم باز است اگر فیلد «کد ملی» قابل مشاهده باشد
    const val IS_OPEN = "(function(){$HELPERS var e=byLabel('کد ملی');return !!(e&&vis(e));})()"

    // data = پروفایل؛ همه فیلدها با برچسب پیدا می‌شوند
    const val FILL = """function(d){
      $HELPERS
      var ev=function(e,n){e.dispatchEvent(new Event(n,{bubbles:true}))};
      var setV=function(e,v){if(!e||v==null||v==='')return;
        var proto=e.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype;
        var s=Object.getOwnPropertyDescriptor(proto,'value').set;
        if(e.tagName==='SELECT'){var ok=false;
          for(var i=0;i<e.options.length;i++){if(e.options[i].text.indexOf(v)>=0){s.call(e,e.options[i].value);ok=true;break}}
          if(!ok)return;
          if(window.jQuery){try{jQuery(e).val(e.value).trigger('change')}catch(x){}}}
        else s.call(e,v);
        ev(e,'input');ev(e,'change');ev(e,'keyup');ev(e,'blur')};
      setV(byLabel('کد ملی'),d.nationalId);
      var mob=document.querySelector('input[placeholder*="9XXXXXXXXX"],input[placeholder*="09"]')||byLabel('موبایل');
      setV(mob,d.mobile);
      setV(byLabel('نام'),d.first);
      setV(byLabel('نام خانوادگی'),d.last);
      setV(byLabel('تاریخ تولد'),d.birth);
      var rs=document.querySelectorAll('input[type=radio]');
      for(var i=0;i<rs.length;i++){var lab=norm((rs[i].parentElement||{}).textContent);
        if(lab.indexOf(d.male?'مرد':'زن')>=0&&(d.male||lab.indexOf('مرد')<0)){rs[i].click();break}}
      setV(byLabel('VIN'),d.vin);
      setV(byLabel('مدل خودرو'),d.model);
      setV(byLabel('کیلومتر فعلی'),d.km);
      setV(byLabel('نوع خودرو'),d.carType);
      // پلاک: بر اساس موقعیت روی صفحه، نه ترتیب DOM
      var lbl=byLabel('شماره پلاک');var box=lbl;
      for(var k=0;k<5&&box;k++){box=box.parentElement;
        if(box&&box.querySelectorAll('input:not([type=hidden]),select').length>=4)break}
      if(box){
        var cs=[].slice.call(box.querySelectorAll('input:not([type=hidden]),select')).filter(vis);
        var sel=cs.filter(function(c){return c.tagName==='SELECT'})[0];
        var ins=cs.filter(function(c){return c.tagName!=='SELECT'});
        ins.sort(function(a,b){return a.getBoundingClientRect().left-b.getBoundingClientRect().left});
        // چپ→راست: دو رقم، (حرف)، سه رقم، کد ایران
        if(ins.length>=3){setV(ins[0],d.plate2);setV(ins[1],d.plate3);setV(ins[2],d.plateCity)}
        if(sel)setV(sel,d.plateLetter);
      }
      // عکس‌ها: اولین input فایل = روی کارت، دومی = پشت کارت
      var fs=document.querySelectorAll('input[type=file]');
      var put=function(inp,data,name){if(!inp||!data)return;
        var a=data.split(',')[1],bin=atob(a),u=new Uint8Array(bin.length);
        for(var i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);
        var f=new File([u],name,{type:'image/jpeg'}),dt=new DataTransfer();dt.items.add(f);
        inp.files=dt.files;ev(inp,'change')};
      put(fs[0],d.frontData,'front.jpg');put(fs[1],d.backData,'back.jpg');
      return 'ok'}"""

    // لیست idها و برچسب‌ها برای عیب‌یابی اگر چیزی پر نشد
    const val INSPECT = """(function(){var o=[];
      document.querySelectorAll('input,select,textarea').forEach(function(e){
        o.push(e.tagName+' type='+e.type+' id='+e.id+' name='+e.name+' ph='+(e.placeholder||'')+' vis='+(e.offsetParent!==null))});
      return o.join('\n')})()"""
}

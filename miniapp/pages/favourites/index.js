const { request, assetUrl } = require("../../utils/api");
Page({
 data:{ favourites:[], recent:[], loading:true, error:"", targetVenueId:0, targetIsFavourite:false },
 async onLoad(options){ this.venueId=Number(options.venueId)||0; this.setData({targetVenueId:this.venueId}); await this.load(); },
 async load(){ this.setData({loading:true,error:""}); try { const [favourites,recent]=await Promise.all([request("/mini/favourite-venues"),request("/mini/recent-venues")]); const favouriteRows=favourites.map(this.decorate); this.setData({favourites:favouriteRows,recent:recent.map(this.decorate),targetIsFavourite:this.venueId > 0 && favouriteRows.some(item=>item.id===this.venueId)}); } catch(e){this.setData({error:e.message});} finally{this.setData({loading:false});} },
 async addTarget(){ if (!this.venueId || this.data.targetIsFavourite) return; try { await request(`/mini/favourite-venues/${this.venueId}`,{method:"POST"}); await this.load(); } catch(e){this.setData({error:e.message});} },
 decorate(item){ return {...item,cover:assetUrl(item.coverPath)}; },
 async toggle(event){ const id=Number(event.currentTarget.dataset.id); const exists=this.data.favourites.some(item=>item.id===id); try { await request(`/mini/favourite-venues/${id}`,{method:exists?"DELETE":"POST"}); await this.load(); } catch(e){this.setData({error:e.message});} },
 open(event){ wx.navigateTo({url:`/pages/venue/index?id=${event.currentTarget.dataset.id}`}); },
});
